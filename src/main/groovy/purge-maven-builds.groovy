import org.sonatype.nexus.repository.storage.Component
import org.sonatype.nexus.repository.storage.Asset
import org.sonatype.nexus.repository.storage.Bucket
import org.sonatype.nexus.repository.storage.Query
import org.sonatype.nexus.common.entity.EntityId
import org.sonatype.nexus.repository.storage.StorageFacet

// For our builds repo, only keep a certain amount of build numbers for each groupId/artifactId/version number.
// For a set of builds, and maxToKeepAssets = 5,
// e.g.
// - au.com/myproject/1.0.0B1
// - au.com/myproject/1.0.0B2
// - au.com/myproject/1.0.0B3
// - au.com/myproject/1.0.0B4
// - au.com/myproject/1.0.0B5
// - au.com/myproject/1.0.0B6
//
// then component "au.com/myproject/1.0.0B1", "au.com/myproject/1.0.0B2", "au.com/myproject/1.0.0B3" would be deleted

// Name of the repository to work on
def repo = repository.repositoryManager.get("maven-builds")
// Maximum number of artifacts to keep per groupId/artifactId/version
def maxToKeepAssets = 3
// Limit assets (for purging) that 'startswith' e.g. "au/com/mycompany/myproject"
def pathsToIncludeStartsWith = null
// Do a dry run and don't change/delete anything
def dryRun = false

def tx = repo.facet(StorageFacet).txSupplier().get()
try {
    log.info("*** Started script - (repo=" + repo.name + ")(maxToKeepAssets=" + maxToKeepAssets + ")(pathsToIncludeStartsWith=" + pathsToIncludeStartsWith + ")(dryRun=" + dryRun + ") ***")
    repo.stop()
    tx.begin()

    Bucket bucket = tx.findBucket(repo)

    def fullAssetList = tx.findAssets(Query.builder().build(), [repo])
    // Key - AssertBasePath and millis as a value
    def referenceMillisBasePaths = new TreeMap<String, String>();
    for (Asset asset : fullAssetList.toList()) {
        if (asset.componentId() != null) {
            if (pathsToIncludeStartsWith == null || asset.name().startsWith(pathsToIncludeStartsWith)) {
                String assetBasePathValue = asset.name().substring(0, asset.name().lastIndexOf("/"))
                referenceMillisBasePaths.putIfAbsent(assetBasePathValue, asset.lastUpdated().millis + "")
            }
        }
    }

    def sortedBasePathAssets = new TreeMap<String, String>()
    for (Asset asset : fullAssetList.toList()) {
        if (asset.componentId() != null) {
            if (pathsToIncludeStartsWith == null || asset.name().startsWith(pathsToIncludeStartsWith)) {
                String assetBasePathValue = asset.name().substring(0, asset.name().lastIndexOf("/"))
                // Key - Prefix base path with common date/time millis for sorting
                String assetBasePathKey = referenceMillisBasePaths.get(assetBasePathValue) + ":" + assetBasePathValue
                sortedBasePathAssets.putIfAbsent(assetBasePathKey, assetBasePathValue)
            }
        }
    }

    // Create a map of assetParentPath(key) and assetBasePaths (list to keep - value)
    def filteredParentPathAssets = new TreeMap<String, List<String>>()
    for (String key : sortedBasePathAssets.descendingKeySet()) {
        String assetBasePath = sortedBasePathAssets.get(key)
        // Remove the last bit of the assetBasePathKey to create the parent path and use it as key
        String assetParentPathKey = assetBasePath.substring(0, assetBasePath.lastIndexOf("/"))
        // Get any existing base path key values
        def assetBasePathsToKeepListValue = filteredParentPathAssets.get(assetParentPathKey)
        if (assetBasePathsToKeepListValue == null) {
            assetBasePathsToKeepListValue = new ArrayList<String>();
            if (maxToKeepAssets > 0) {
                assetBasePathsToKeepListValue.add(assetBasePath)
            }
            filteredParentPathAssets.put(assetParentPathKey, assetBasePathsToKeepListValue)
        } else {
            if (assetBasePathsToKeepListValue.size() < maxToKeepAssets) {
                assetBasePathsToKeepListValue.add(assetBasePath)
            }
        }
    }

    // Create a map of assetBasePaths(key) to keep
    def assetBasePathsToKeep = new TreeMap<String, String>()
    for (String key : filteredParentPathAssets.keySet()) {
        for (String assetBasePath : filteredParentPathAssets.get(key)) {
            assetBasePathsToKeep.put(assetBasePath, assetBasePath)
        }
    }

    def assetPathsToDelete = new TreeMap<String, String>()
    def componentsToDelete = new HashMap<EntityId, Component>()
    def componentsToDeleteReport = new TreeMap<String, Component>()
    for (Asset asset : fullAssetList.toList()) {
        if (asset.componentId() != null) {
            if (pathsToIncludeStartsWith == null || asset.name().startsWith(pathsToIncludeStartsWith)) {
                String assetBasePath = asset.name().substring(0, asset.name().lastIndexOf("/"))
                if (assetBasePathsToKeep.get(assetBasePath) == null) {
                    assetPathsToDelete.put(asset.name(), asset.name())
                    if (componentsToDelete.get(asset.componentId()) == null) {
                        Component foundComponent = tx.findComponent(asset.componentId(), bucket)
                        if (foundComponent != null) {
                            componentsToDelete.put(asset.componentId(), foundComponent)
                            componentsToDeleteReport.put(asset.componentId().toString(), foundComponent)
                        }
                    }
                    log.info("Deleting asset: " + asset.name())
                    if (!dryRun) {
                        tx.deleteAsset(asset)
                    }
                }
            }
        }
    }

    for (EntityId id : componentsToDelete.keySet()) {
        Component deleteComponent = tx.findComponent(id, bucket)
        if (deleteComponent != null) {
            if (!dryRun) {
                tx.deleteComponent(deleteComponent)
            }
            log.info("Deleted component: " + deleteComponent.toString())
        }
    }

    for (String key : assetBasePathsToKeep.keySet()) {
        log.info("Keeping assets in base path: " + key)
    }

    tx.commit()
} catch (Exception ex) {
    log.info("Error occurred: " + ex)
} finally {
    repo.start()
    tx.close()
}

log.info("*** Finished script - (repo=" + repo.name + ")(maxToKeepAssets=" + maxToKeepAssets + ")(pathsToIncludeStartsWith=" + pathsToIncludeStartsWith + ")(dryRun=" + dryRun + ") ***")
