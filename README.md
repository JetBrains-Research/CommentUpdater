# CommentUpdater
A plugin for IntelliJ IDEA that detects outdated comments in Java code


## Launch instructions

### Plugin

- Inside the `comment-updater-plugin/src/main/kotlin/...models/config/ModelFilesConfig.kt` file 
replace `DATA_DIR` value with your local path to the directory, containing files from [here](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing)
  
- Run  `gradle/comment-updater-plugin/intellij/runIde` task

### Headless Plugin 

- Inside the `comment-updater-plugin/src/main/kotlin/...models/config/ModelFilesConfig.kt` file
  replace `DATA_DIR` value with your local path to the directory, containing files from [here](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing)

- Inside the `comment-updater-headless/src/main/kotlin/HeadlessConfig.kt` file
  replace `OUTPUT_DIR_PATH` and `INPUT_FILE` values with your local paths
  
- `INPUT_FILE` should contain absolute paths to the projects, which you want to process 
  