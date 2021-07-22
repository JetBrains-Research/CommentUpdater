# CommentUpdater Plugin
A plugin for IntelliJ IDEA that detects outdated comments in Java code

### How to launch plugin?
1. Clone project `git clone https://github.com/JetBrains-Research/CommentUpdater.git`
2. Download an [archive](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing) with model and code embeddings;
3. Open CommentUpdater project, go to `comment-updater-plugin/src/main/kotlin/...models/config/` and modify file `ModelFilesConfig.kt` by replacing `datadDir` value with the path to the directory containing files from Step 2;
4. Run `gradle/comment-updater-plugin/intellij/runIde` task.


# CommentUpdater Headless Plugin 

Headless plugin is a CLI tool for collecting code-comment inconsistencies from existing projects. The main purpose of this tool - 
unsupervised machine learning dataset collection. You can pass a list of projects as input and receive a list of code comment changes
from those projects, labeled as CONSISTENT or INCONSISTENT. To learn more about the formulation of the machine learning problem,
take a look [here](https://github.com/JetBrains-Research/comment-updating/wiki/Papers-summary).

### How to launch headless plugin?
- run `commentupdater.sh` file with three arguments: 
  1. path to the file, containing absolute paths to the projects, which you want to process, separated with `\n` . For example: 
     ```
     dir1/project1/
     dir2/childDir2/project2/
     project3/
     ```
  2. path to the directory, where resulting files with code-comment samples would be written
  3. path to the directory, containing files from [here](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing)
  
Launch example:
`./commentupdater.sh input.txt dataset/ modelConfig/`