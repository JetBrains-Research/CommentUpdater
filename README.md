# CommentUpdater
A plugin for IntelliJ IDEA that detects outdated comments in Java code


## Launch instructions

### Plugin

- Inside the `comment-updater-plugin/src/main/kotlin/...models/config/ModelFilesConfig.kt` file 
replace default `dataDir` argument with path to the directory, containing files from [here](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing)
  
- Run  `gradle/comment-updater-plugin/intellij/runIde` task

### Headless Plugin 

- run `commentupdater.sh` file with three arguments: 
  1. path to the file containing absolute paths to the projects, which you want to process
  2. path to the directory, where resulting files with samples would be written
  3. path to the directory, , containing files from [here](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing)
  