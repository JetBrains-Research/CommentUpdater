#!/usr/bin/env bash

if [ $# -ne "4" ]; then
    echo "usage: ./comment_update_miner.sh <path to project list file> <path to output folder> <path to model config> <path to statistic output>"
    exit 1
fi

# https://stackoverflow.com/a/246128
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
if uname -s | grep -iq cygwin ; then
    DIR=$(cygpath -w "$DIR")
    PWD=$(cygpath -w "$PWD")
fi

"$DIR/gradlew" --stop
"$DIR/gradlew" -p "$DIR" clean
echo "DIR $DIR"
echo "PWD $PWD"
"$DIR/gradlew" -p "$DIR" runCommentUpdater -Prunner="CommentUpdater" -Pdataset="$PWD/$1" -Poutput="$PWD/$2" -Pconfig="$PWD/$3" -PstatsOutput="$PWD/$4" --stacktrace