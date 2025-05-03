#!/bin/bash

set -e

CWD=$(pwd)
TMP_DIR=$(mktemp --directory)
cd "${TMP_DIR}"

PREFIX=${1:-/usr/local}
mkdir -p "${PREFIX}/include"

echo Installing atomic_queue...
git clone https://github.com/max0x7ba/atomic_queue.git
(cd atomic_queue && cp -f -r include/atomic_queue "${PREFIX}/include")
echo Done installing atomic_queue.
echo

echo Installing moodycamel::ConcurrentQueue...
git clone https://github.com/cameron314/concurrentqueue.git
(cd concurrentqueue && cmake -DCMAKE_INSTALL_PREFIX:PATH="${PREFIX}" . && make install)
echo Done installing moodycamel::ConcurrentQueue.
echo

echo Installing xenium...
git clone https://github.com/mpoeter/xenium.git
(cd xenium && cp -f -r xenium "${PREFIX}/include")
echo Done installing xenium.
echo

cd "${CWD}"
rm -rf "${TMP_DIR}"
