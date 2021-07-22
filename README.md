# CommentUpdater
A plugin for IntelliJ IDEA that detects outdated comments in Java code.

## Usage
There are two ways to use CommentUpdater:
* As a plugin for IntelliJ IDEA that highlights your attention to outdated code comments;
* As a CLI tool to generate dataset of code-comment inconsistencies from existing Java projects.

### Running plugin
1. Clone project `git clone https://github.com/JetBrains-Research/CommentUpdater.git`
2. Download an [archive](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing) with model and code embeddings;
3. Open CommentUpdater project, go to `comment-updater-plugin/src/main/kotlin/...models/config/` and modify file `ModelFilesConfig.kt` by replacing `datadDir` value with the path to the directory containing files from Step 2;
4. Run `gradle/comment-updater-plugin/intellij/runIde` task.

### Running CLI
The main purpose of CLI is collecting dataset of consistent and inconsistent samples in Java projects.

Run a script `commentupdater.sh` with three arguments: 
1. Path to the file containing absolute paths to the projects, which you want to process, separated with `\n`.
Example: 
     ```
     dir1/project1/
     dir2/project2/
     dir3/project3/
     ```
2. Path to the directory where resulting files with code-comment samples would be written;
3. Path to the directory containing files (model and code embeddings) from [the archive](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing).
  
Launch example:
`./commentupdater.sh input.txt dataset/ modelConfig/`
