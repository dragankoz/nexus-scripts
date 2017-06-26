import com.sun.xml.internal.ws.api.Component
import org.sonatype.nexus.repository.storage.Component
import org.sonatype.nexus.repository.storage.Query
import org.sonatype.nexus.repository.storage.StorageFacet

// Purge all components which do not match the version mask defined in 'matchingVersionsToKeep'

// Name of the repository to work on
def repo = repository.repositoryManager.get("maven-releases")
// Hard versions to keep e.g. 10, 10.2. 10.2.5, 10.2.5.40 etc
def matchingVersionsToKeep = "^[0-9]+(\\.[0-9]+)*"
// Only match specific release versions
def matchingVersionsToConsiderDeleting = null // e.g '.*\\.[bB].*$'
// Do a dry run and don't change/delete anything
def dryRun = false

def tx = repo.facet(StorageFacet).txSupplier().get()
try {
    log.info("*** Started script - (repo=" + repo.name + ")(matchingVersionsToKeep=" + matchingVersionsToKeep +
            ")(matchingVersionsToConsiderDeleting=" + matchingVersionsToConsiderDeleting + ")(dryRun=" + dryRun + ") ***")
    repo.stop()
    tx.begin()

    // Find all components for this repo
    def fullComponentsList = tx.findComponents(Query.builder().build(), [repo])

    def componentsToKeepReport = new TreeMap<String, Component>()
    def componentsToKeep = new HashMap<Component, Component>()
    def componentsToConsider = new HashMap<Component, Component>()

    for (Component component : fullComponentsList.toList()) {
        // Create a map of components to keep and delete
        if (component.version().matches(matchingVersionsToKeep)) {
            componentsToKeep.putIfAbsent(component, component)
            componentsToKeepReport.putIfAbsent(String.format("%s:%s:%s",component.group(), component.name(), component.version()),component)
        } else if (matchingVersionsToConsiderDeleting == null || component.version().matches(matchingVersionsToConsiderDeleting)) {
            componentsToConsider.putIfAbsent(component, component)
        }
    }

    def componentsToDeleteReport = new TreeMap<String, Component>()
    def componentsToDelete = new HashMap<Component, Component>()
    for (Component componentToConsider : componentsToConsider.keySet()) {
        // Loop thru all components to keep and see if we can find a hard release version
        for (Component componentKeep : componentsToKeep.keySet()) {
            // Loop thru all components to keep and see if we can find a hard release version
            if (componentToConsider.name().equals(componentKeep.name()) && componentToConsider.group().equals(componentKeep.group())) {
                // Take the component version number (to be considered for deletion) and see if it matches a 'keep' version
                // e.g '1.0.0-B1'.startWith('1.0.0.')
                if (componentToConsider.version().startsWith(componentKeep.version() + ".") || componentToConsider.version().startsWith(componentKeep.version() + "-")) {
                    // If we need to further version match
                    if (matchingVersionsToConsiderDeleting == null || componentToConsider.version().matches(matchingVersionsToConsiderDeleting)) {
                        // If we hard a 'keep' version then delete this component
                        componentsToDelete.putIfAbsent(componentToConsider, componentToConsider)
                        componentsToDeleteReport.putIfAbsent(String.format("%s:%s:%s", componentToConsider.group(), componentToConsider.name(), componentToConsider.version()), componentToConsider)
                    }
                }
            }
        }
    }

    for (String componentKeepString : componentsToKeepReport.keySet()) {
        log.info("Keeping Component: " + componentKeepString)
    }
    for (String componentDeleteString : componentsToDeleteReport.keySet()) {
        log.info("Deleting Component: " + componentDeleteString)
    }

    if (!dryRun) {
        for (Component componentDelete : componentsToDelete.keySet()) {
            tx.deleteComponent(componentDelete)
        }
    }

    tx.commit()
} catch (Exception ex) {
    log.info("Error occurred: " + ex)
} finally {
    repo.start()
    tx.close()
}

log.info("*** Finished script - (repo=" + repo.name + ")(matchingVersionsToKeep=" + matchingVersionsToKeep +
        ")(matchingVersionsToConsiderDeleting=" + matchingVersionsToConsiderDeleting + ")(dryRun=" + dryRun + ") ***")