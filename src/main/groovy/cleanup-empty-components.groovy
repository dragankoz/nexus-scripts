import org.sonatype.nexus.repository.storage.Component
import org.sonatype.nexus.repository.storage.Asset
import org.sonatype.nexus.repository.storage.Bucket
import org.sonatype.nexus.common.entity.EntityId
import org.sonatype.nexus.repository.storage.Query
import org.sonatype.nexus.repository.storage.StorageFacet

def repo = repository.repositoryManager.get("maven-builds")
def dryRun = false

def tx = repo.facet(StorageFacet).txSupplier().get()
try {
    log.info("*** Started script - (repoName=" + repo.name + ")(dryRun=" + dryRun + ") ***")

    tx.begin()
    Bucket bucket = tx.findBucket(repo)
    // Find all components for this repo
    def fullComponentsList = tx.findComponents(Query.builder().build(), [repo])
    def componentIdMap = new HashMap<EntityId, Component>()
    for (Component component : fullComponentsList.toList()) {
        if (component.name().contains("mebs") || component.group().contains("mebs")) {
            componentIdMap.putIfAbsent(component.getEntityMetadata().getId(), component)
        }
    }

    // All assets for this repo
    def fullAssetList = tx.findAssets(Query.builder().build(), [repo])
    // Key - AssertBasePath and millis as a value
    for (Asset asset : fullAssetList.toList()) {
        if (asset.componentId() != null && asset.name().contains("mebs")) {
            componentIdMap.remove(asset.componentId())
        }
    }

    for (EntityId entityId : componentIdMap.keySet()) {
        Component deleteComponent = tx.findComponent(entityId,  bucket)
        if (deleteComponent != null) {
            log.info("Deleting Component: " + componentIdMap.get(entityId))
            if (!dryRun) {
                tx.deleteComponent(deleteComponent)
            }
        }
    }
    tx.commit()
} catch (Exception ex) {
    log.info("Error occurred: " + ex)
} finally {
    tx.close()
}
log.info("*** Finished script - (repoName=" + repo.name + ")(dryRun=" + dryRun + ") ***")
