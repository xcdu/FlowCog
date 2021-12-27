#!/usr/bin/env python
# -*- coding: utf-8 -*-
import argparse
import os
import os.path
import signal
import subprocess


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk_dir")
    parser.add_argument("android_platforms")
    parser.add_argument("sources_sinks")
    parser.add_argument("logs_dir")
    parser.add_argument("-t", "--timeout", type=int, default=-1)
    return parser.parse_args()


def find_apks(apk_dir):
    paths = []
    for root, subdir, files in os.walk(apk_dir):
        for filename in files:
            if filename.endswith(".apk"):
                paths.append(os.path.join(root, filename))
    return paths


def find_file(directory, filename):
    for root, subdir, files in os.walk(directory):
        for f in files:
            if f == filename:
                print("root = " + str(root))
                print("f = " + str(f))
                return os.path.join(root, f)
    return None


def get_process_children(pid):
    p = subprocess.Popen('ps --no-headers -o pid --ppid %d' % pid, shell=True,
                         stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = p.communicate()
    return [int(p) for p in stdout.split()]


def execute(jar, apk, android_platforms, sources_sinks, log_path, timeout):
    class Alarm(Exception):
        pass

    def alarm_handler(signum, frame):
        raise Alarm

    command = " ".join([
        "java", "-Xmx64g",
        "-jar", jar,
        "-a", apk,
        "-p", android_platforms,
        "-s", sources_sinks,
        "-rt", str(int(timeout * 0.75)),
        ">", log_path, "2>&1"
    ])
    print(command)
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE)
    if timeout != -1:
        signal.signal(signal.SIGALRM, alarm_handler)
        signal.alarm(timeout)
    try:
        p.communicate()
        if timeout != -1:
            signal.alarm(0)
    except Alarm:
        pids = [p.pid]
        pids.extend(get_process_children(p.pid))
        for pid in pids:
            # process might have died before getting to this line
            # so wrap to avoid OSError: no such process
            try:
                os.kill(pid, signal.SIGKILL)
            except OSError:
                pass
        return -1
    return p.returncode


def batch(args):
    apk_dir = os.path.abspath(args.apk_dir)
    android_platforms = os.path.realpath(args.android_platforms)
    sources_sinks = os.path.realpath(args.sources_sinks)
    logs_dir = os.path.realpath(args.logs_dir)
    timeout = args.timeout
    jar = find_file(os.getcwd(), "flowcog-android.jar")
    print("jar = " + str(jar))
    apks = find_apks(apk_dir)
    for i, apk in enumerate(apks):
        print("{}/{}".format(i, len(apks)))
        log_path = os.path.join(logs_dir, os.path.basename(apk) + ".log")
        return_code = execute(jar, apk, android_platforms, sources_sinks, log_path, timeout)
        print("return_code = " + str(return_code))


def main():
    args = parse_args()
    batch(args)


if __name__ == '__main__':
    main()
