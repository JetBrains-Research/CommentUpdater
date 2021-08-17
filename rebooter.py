import subprocess
import threading
from collections import deque
from typing import Optional, List
import os
import sys
from itertools import islice
import re
import datetime
import signal
import shutil
import atexit

PROCESSED = 0
TOTAL = 0
START_TIME = datetime.datetime.now()

def log(message: str, tag: str = "[PYTHON INFO]", log_file='python_log.txt'):
    #print(f"{tag} {message}")
    elapsed_time = (datetime.datetime.now() - START_TIME)
    with open(log_file, 'a') as log_file:
        log_file.write(f"{elapsed_time} {tag} {message}\n")


def chunk(it, size):
    it = iter(it)
    return iter(lambda: tuple(islice(it, size)), ())


def check_opening(content: str, opening_tag="opening"):
    return opening_tag in content


def extract_project(log_state: str) -> str:
    projects = re.findall(r"\[.*?]", log_state)
    if not projects:
        return ""
    return projects[0][1:-1]

def check_low_memory(last_logs: List[str]) -> bool:
    low_warning = "Low memory"
    warning_constant = 4
    count_low_warnings = sum([int(low_warning in log) for log in last_logs])
    return count_low_warnings >= warning_constant

PROCESS = None

def cleanup():
    if PROCESS:
        os.killpg(os.getpgid(PROCESS.pid), signal.SIGTERM)
        print("Killed process by cleaning up")

def run(cmd, timeout, log_path, idea_log_path):
    process: Optional[subprocess.Popen] = None

    def target():
        nonlocal process
        log("Thread started\n")
        process = subprocess.Popen(cmd, shell=True, preexec_fn=os.setsid)
        process.communicate()
        log("Thread finished")

    thread = threading.Thread(target=target)
    thread.start()

    last_log_suffix_size = 60
    opening = False
    terminated_project = None
    opening_project = None
    global PROCESS
    PROCESS = process
    while True:
        log("Waiting for timeout")
        thread.join(timeout)
        if thread.is_alive():
            log("Timeout reached, checking script state")


            with open(log_path, 'r') as log_file:
                opening_state = log_file.read().strip()


            #with open(idea_log_path, 'r') as idea_log_file:
            log("Copying idea logs...")

            shutil.copyfile(idea_log_path, './idea.log')

            log("Idea logs copied")

            with open('./idea.log', 'r') as idea_log_file:
                log("Reading idea logs...")
                idea_log_content = idea_log_file.read().split('\n')
                last_idea_logs = idea_log_content[-last_log_suffix_size:]
                log("Idea logs read")

            if check_low_memory(last_idea_logs):
                log("Low memory detected, terminating...")
                terminated_project = extract_project(opening_state)
                with open(log_path, 'w') as log_file:
                    log_file.write("")
                os.killpg(os.getpgid(process.pid), signal.SIGTERM)
                thread.join()
                break


            log(f"OPENING STATE: {opening_state}")
            if opening and check_opening(opening_state) and opening_project == extract_project(opening_state):
                terminated_project = extract_project(opening_state)
                log(f"Too long opening, terminating process on project: {terminated_project}")
                # Clean up timeout log file before the kill
                with open(log_path, 'w') as log_file:
                    log_file.write("")
                os.killpg(os.getpgid(process.pid), signal.SIGTERM)
                thread.join()
                break
            if check_opening(opening_state):
                log("Opening detected")
                opening = True
                opening_project = extract_project(opening_state)
            else:
                opening = False
                opening_project = None
                log(f"OK script state")
        else:
            log("Thread gracefully finished")
            break
    log(str(process.returncode))
    return terminated_project

def save_logs(logs_dir, idea_log_path):
    logs_num = len(os.listdir(logs_dir))
    log("Copying idea logs...")
    shutil.copyfile(idea_log_path, os.path.join(logs_dir, f'idea{logs_num}.log'))
    log("Idea logs copied")


if __name__ == '__main__':

    atexit.register(cleanup)


    if len(sys.argv) != 6:
        print("""
                # args:
                # <path to output folder>
                # <path to model config>
                # <path to statistic output>
                # <path to timeout logs>
                # <path to input dataset>
        """)
        exit(0)

    dataset_path = os.path.join(os.curdir, sys.argv[5])
    with open(dataset_path, 'r') as dataset_file:
        dataset = dataset_file.read().strip().split('\n')

    # cmd = """
    #     echo "[HeadlessCommentUpdater] kek";
    #     sleep 2;
    #     echo "[HeadlessCommentUpdater] kek1";
    #     sleep 2;
    #     echo "[HeadlessCommentUpdater] kek2";
    #     sleep 2;
    #     echo "[HeadlessCommentUpdater] kek3";
    #     sleep 2;
    #     echo "[HeadlessCommentUpdater] kek4";
    #     sleep 2;
    #     echo "[HeadlessCommentUpdater] kek5";
    #     sleep 2;
    #     echo "[HeadlessCommentUpdater] lol";
    # """
    # run(timeout=3,
    #     cmd=cmd)

    log_path = os.path.join(os.curdir, sys.argv[4])

    dir_to_save_logs = os.path.join(os.curdir, 'logs')
    if not os.path.exists(dir_to_save_logs):
        os.makedirs(dir_to_save_logs)

    idea_log_path = '/home/ubuntu/CommentUpdater/comment-updater-headless/build/idea-sandbox/system/log/idea.log'
    batch_size = 20
    timeout = 60 * 10
    script = "./comment_update_miner.sh"
    project_q = deque()
    project_q.extend(dataset)
    TOTAL = len(dataset)
    while project_q:
        batch = []
        while len(batch) < batch_size and len(project_q) > 0:
            batch.append(project_q.popleft())

        with open('batch_input.txt', 'w') as batch_file:
            batch_file.write('\n'.join(batch))

        cmd = f"{script} batch_input.txt {sys.argv[1]} {sys.argv[2]} {sys.argv[3]} {sys.argv[4]}"
        timeout_pr = run(timeout=timeout, cmd=cmd, log_path=log_path, idea_log_path=idea_log_path)
        save_logs(dir_to_save_logs, idea_log_path)
        if timeout_pr is not None:
            batch_prs = list(map(lambda x: x.split(os.sep)[-1], batch))
            pos = batch_prs.index(timeout_pr)
            # timeout happened at pos, all projects before - processed, and after - should be added to the queue
            project_q.extendleft(batch[pos + 1:])
            PROCESSED += pos
        else:
            PROCESSED += batch_size
