# CommentUpdater
A plugin for IntelliJ IDEA that detects outdated comments in Java code


## Plugin

- Inside the `comment-updater-plugin/src/main/kotlin/...models/config/ModelFilesConfig.kt` file 
replace default `dataDir` argument with path to the directory, containing files from [here](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing)
  
- Run  `gradle/comment-updater-plugin/intellij/runIde` task

## Headless Plugin 

Headless part of the plugin collects consistency and inconsistency examples from existing projects. 
You can provide paths to locally stored git projects and plugin will collect consistency and inconsistency samples. 

Launch: 
- run `comment_inconsistency_miner.sh` file with four arguments: 
  1. path to the file containing absolute paths to the projects, which you want to process
  2. path to the directory, where resulting files with samples would be written
  3. path to the directory, containing files from [here](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing)
  4. path to output file, where json list of extracted dataset samples should be saved
  
Launch example:
```
./comment_insonsistency_miner.sh input.txt dataset modelConfig output.json
```
Where:
- input.txt: 
  ```
  /Dir1/project1
  /Dir2/project2
  /Dir2/project3
  ```
- dataset - empty folder (where unlabeled samples for projects would be stored)
- modelConfig - folder with content from [link](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing)
- output.json - empty file (where resulting labeled samples from all projects would be stored)