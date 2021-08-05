import subprocess
import threading
from collections import deque
from typing import Optional
import os
import sys
from itertools import islice
import re
import datetime
import signal

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


def run(cmd, timeout, log_path):
    process: Optional[subprocess.Popen] = None

    def target():
        nonlocal process
        log("Thread started\n")
        process = subprocess.Popen(cmd, shell=True, preexec_fn=os.setsid)
        process.communicate()
        log("Thread finished")

    thread = threading.Thread(target=target)
    thread.start()

    opening = False
    terminated_project = None
    while True:
        log("Waiting for timeout")
        thread.join(timeout)
        if thread.is_alive():
            log("Timeout reached, checking script state")
            with open(log_path, 'r') as log_file:
                opening_state = log_file.read().strip()
            log(f"OPENING STATE: {opening_state}")
            if opening and check_opening(opening_state):
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
            else:
                opening = False
                log(f"OK script state")
        else:
            log("Thread gracefully finished")
            break
    log(str(process.returncode))
    return terminated_project


if __name__ == '__main__':
    dataset_path = '/Users/Ivan.Pavlov/IdeaProjects/input.txt'
    with open(dataset_path, 'r') as dataset_file:
        dataset = dataset_file.read().strip().split('\n')

    if len(sys.argv) != 5:
        print("""
                # args:
                # <path to output folder>
                # <path to model config>
                # <path to statistic output>
                # <path to timeout logs>
        """)
        exit(0)

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

    batch_size = 5
    timeout = 3
    script = "./comment_update_miner.sh"
    project_q = deque()
    project_q.extend(dataset)
    TOTAL = len(dataset)
    while project_q:
        print(list(map(lambda x: x.split(os.sep)[-1], project_q)))
        batch = []
        while len(batch) < batch_size and len(project_q) > 0:
            batch.append(project_q.popleft())

        with open('batch_input.txt', 'w') as batch_file:
            batch_file.write('\n'.join(batch))

        cmd = f"{script} batch_input.txt {sys.argv[1]} {sys.argv[2]} {sys.argv[3]} {sys.argv[4]}"
        timeout_pr = run(timeout=timeout, cmd=cmd, log_path=log_path)
        if timeout_pr is not None:
            batch_prs = list(map(lambda x: x.split(os.sep)[-1], batch))
            pos = batch_prs.index(timeout_pr)
            # timeout happened at pos, all projects before - processed, and after - should be added to the queue
            project_q.extendleft(batch[pos + 1:])
            PROCESSED += pos
        else:
            PROCESSED += batch_size
