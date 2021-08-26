# CommentUpdater

A plugin for IntelliJ IDEA that detects outdated comments in Java code.

## Usage

There are two ways to use CommentUpdater:

* As a plugin for IntelliJ IDEA that highlights your attention to outdated code comments;
* As a CLI tool to generate dataset of code-comment inconsistencies from existing Java projects.

### Running plugin

1. Clone project `git clone https://github.com/JetBrains-Research/CommentUpdater.git`
2. Download an [archive](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing) with
   model and code embeddings;
3. Open CommentUpdater project, go to `comment-updater-plugin/src/main/kotlin/...models/config/` and modify
   file `ModelFilesConfig.kt` by replacing `datadDir` value with the path to the directory containing files from Step 2;
4. Run `gradle/comment-updater-plugin/intellij/runIde` task.

### Running CLI

The main purpose of CLI is collecting dataset of consistent and inconsistent samples in Java projects.

Run a script `commentupdater.sh` with three arguments:

1. Path to the file containing absolute paths to the projects, which you want to process, separated with `\n`. Example:
     ```
     dir1/project1/
     dir2/project2/
     dir3/project3/
     ```
2. Path to the directory where resulting files with code-comment samples would be written;
3. Path to the directory containing files (model and code embeddings)
   from [the archive](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing).

Launch example:
`./commentupdater.sh input.txt dataset/ modelConfig/`

Headless part of the plugin collects consistency and inconsistency examples from existing projects. You can provide
paths to locally stored git projects and plugin will collect consistency and inconsistency samples.

Launch:

- run `comment_inconsistency_miner.sh` file with five arguments:
    1. path to the file containing absolute paths to the projects, which you want to process
    2. path to the directory, where resulting files with samples would be written
    3. path to the directory, containing files
       from [here](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing)
    4. path to output file, where json list of extracted dataset samples should be saved
    5. path to output statistic file, where project statistics should be saved

Launch example:

```
./comment_insonsistency_miner.sh input.txt dataset modelConfig output.json stats.json
```

Where:

- input.txt:
  ```
  /Dir1/project1
  /Dir2/project2
  /Dir2/project3
  ```
- dataset - empty folder (where unlabeled samples for projects would be stored)
- modelConfig - folder with content
  from [link](https://drive.google.com/drive/folders/1E4XOJHfEWOlHXBPxPu3_bkKfuU4G_N49?usp=sharing)
- output.json - empty file (where resulting labeled samples from all projects would be stored)
- stats.json - empty file


### Statistics

You can find notebook with the collected projects statistics analysis here: [a relative link](DatasetStatitstics.ipynb)