pluginManagement { repositories { google(); maven("https://maven-central.storage-download.googleapis.com/maven2/"); maven("https://repo1.maven.org/maven2/") } }
dependencyResolutionManagement { repositories { google(); maven("https://maven-central.storage-download.googleapis.com/maven2/"); maven("https://repo1.maven.org/maven2/") } }
rootProject.name = "Siftra"
include(":app")
