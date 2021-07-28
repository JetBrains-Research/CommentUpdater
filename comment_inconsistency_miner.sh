#!/usr/bin/env bash

if [ $# -ne "5" ]; then
    echo "usage: ./comment_inconsistency_miner.sh <path to project list file> <path to output folder> <path to model config> <path to dataset output file> <path to stats output file>"
    exit 1
fi

# https://stackoverflow.com/a/246128
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
if uname -s | grep -iq cygwin ; then
    DIR=$(cygpath -w "$DIR")
    PWD=$(cygpath -w "$PWD")
fi


sh ./comment_update_miner.sh "$1" "$2" "$3" "$5"

# copy headless logs
cp ./comment-updater-headless/build/idea-sandbox/system/log/idea.log ./headless.log

sh ./postprocessing.sh "$2" "$4" "$3"

# copy postprocessing logs
cp ./comment-updater-postprocessing/build/idea-sandbox/system/log/idea.log ./postprocessing.log
