#!/usr/bin/python3

import json
import os
import re
import selectors
import stat
import subprocess
import sys
import time
from pathlib import Path

ROOT_CONFIG = Path("/etc/grimmory/synology-acl-root")
TOOL = "/usr/syno/bin/synoacltool"
MAX_INPUT = 16 * 1024
MAX_TOOL_OUTPUT = 16 * 1024
MAX_RESPONSE = 32 * 1024
REQUEST_TIMEOUT_SECONDS = 10
UUID = r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
TEMP_NAME = re.compile(rf"\.grimmory-{UUID}\.(candidate|backup)\Z")


class HelperError(Exception):
    pass


def fail():
    sys.stderr.write("ACL helper rejected or failed the request\n")
    return 1


def read_root():
    fd = os.open(ROOT_CONFIG, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    try:
        info = os.fstat(fd)
        if info.st_uid != 0 or info.st_mode & 0o022 or not stat.S_ISREG(info.st_mode):
            raise HelperError("Unsafe ACL root configuration")
        raw = os.read(fd, 4097)
    finally:
        os.close(fd)
    if len(raw) > 4096:
        raise HelperError("ACL root configuration is too large")
    value = raw.decode("utf-8").strip()
    root = Path(value)
    if not root.is_absolute() or "\n" in value or "\r" in value:
        raise HelperError("Invalid ACL root")
    resolved = root.resolve(strict=True)
    if resolved != root or not resolved.is_dir():
        raise HelperError("ACL root must be a canonical directory")
    return resolved


def read_request():
    raw = bytearray()
    selector = selectors.DefaultSelector()
    selector.register(sys.stdin.buffer, selectors.EVENT_READ)
    deadline = time.monotonic() + REQUEST_TIMEOUT_SECONDS
    try:
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0 or not selector.select(remaining):
                raise HelperError("Request timed out")
            chunk = os.read(sys.stdin.fileno(), min(4096, MAX_INPUT + 1 - len(raw)))
            if not chunk:
                break
            raw.extend(chunk)
            if len(raw) > MAX_INPUT:
                raise HelperError("Request is too large")
    finally:
        selector.close()
    try:
        request = json.loads(bytes(raw).decode("utf-8"), object_pairs_hook=unique_object)
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise HelperError("Invalid request") from None
    if not isinstance(request, dict):
        raise HelperError("Invalid request")
    return request


def unique_object(items):
    result = {}
    for key, value in items:
        if key in result:
            raise HelperError("Duplicate request field")
        result[key] = value
    return result


def relative_parts(value):
    if not isinstance(value, str) or not value or len(value) > 4096:
        raise HelperError("Invalid path")
    if value.startswith("/") or "\\" in value or any(ord(char) < 32 or ord(char) == 127 for char in value):
        raise HelperError("Invalid path")
    parts = value.split("/")
    if any(part in ("", ".", "..") for part in parts):
        raise HelperError("Invalid path")
    return parts


def open_file(root, parts):
    directory_fd = os.open(
        str(root),
        os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0),
    )
    try:
        for part in parts[:-1]:
            next_fd = os.open(
                part,
                os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0),
                dir_fd=directory_fd,
            )
            os.close(directory_fd)
            directory_fd = next_fd
        file_fd = os.open(
            parts[-1],
            os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0),
            dir_fd=directory_fd,
        )
    finally:
        os.close(directory_fd)
    info = os.fstat(file_fd)
    if not stat.S_ISREG(info.st_mode):
        os.close(file_fd)
        raise HelperError("ACL targets must be regular files")
    return file_fd, info


def fd_path(fd):
    return f"/proc/self/fd/{fd}"


def validate_epub_or_temp(name):
    if name.lower().endswith(".epub") or TEMP_NAME.fullmatch(name):
        return
    raise HelperError("Only EPUBs and operation temporary files are allowed")


def run_tool(*args, pass_fds=()):
    process = subprocess.Popen(
        [TOOL, *[str(arg) for arg in args]],
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        pass_fds=pass_fds,
        env={"PATH": "/usr/bin:/bin:/usr/syno/bin", "LD_LIBRARY_PATH": "/usr/lib"},
    )
    output = {"stdout": bytearray(), "stderr": bytearray()}
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ, "stdout")
    selector.register(process.stderr, selectors.EVENT_READ, "stderr")
    deadline = time.monotonic() + 10
    try:
        while selector.get_map():
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise HelperError("Synology ACL command timed out")
            for key, _ in selector.select(remaining):
                chunk = os.read(key.fileobj.fileno(), 4096)
                if not chunk:
                    selector.unregister(key.fileobj)
                    key.fileobj.close()
                    continue
                buffer = output[key.data]
                buffer.extend(chunk)
                if len(buffer) > MAX_TOOL_OUTPUT:
                    raise HelperError("Synology ACL command returned too much output")
        if process.wait(timeout=max(0, deadline - time.monotonic())) != 0:
            raise HelperError("Synology ACL command failed")
    except subprocess.TimeoutExpired:
        raise HelperError("Synology ACL command timed out") from None
    finally:
        selector.close()
        if process.poll() is None:
            process.kill()
            process.wait()
    if len(output["stdout"]) > MAX_TOOL_OUTPUT or len(output["stderr"]) > MAX_TOOL_OUTPUT:
        raise HelperError("Synology ACL command failed")
    return bytes(output["stdout"]).decode("utf-8", errors="strict")


def snapshot(fd, info):
    path = fd_path(fd)
    output = run_tool("-get", path, pass_fds=(fd,))
    archives = [line.strip() for line in output.splitlines() if line.startswith("Archive:")]
    if len(archives) != 1 or "is_support_ACL" not in archives[0]:
        raise HelperError("ACL support is unavailable")
    if not any(flag in archives[0] for flag in ("is_inherit", "has_ACL")):
        raise HelperError("ACL state is unsupported")
    entries = run_tool("-getace", path, pass_fds=(fd,)).rstrip()
    if not entries or len(entries) > MAX_TOOL_OUTPUT:
        raise HelperError("ACL entries are unavailable")
    return {
        "uid": info.st_uid,
        "gid": info.st_gid,
        "mode": stat.S_IMODE(info.st_mode),
        "archive": archives[0],
        "entries": entries,
    }


def validate_snapshot(value):
    if not isinstance(value, dict) or set(value) != {"uid", "gid", "mode", "archive", "entries"}:
        raise HelperError("Invalid expected snapshot")
    if any(type(value[key]) is not int or value[key] < 0 for key in ("uid", "gid", "mode")):
        raise HelperError("Invalid expected identity")
    if value["mode"] > 0o7777 or not isinstance(value["archive"], str) or len(value["archive"]) > 512:
        raise HelperError("Invalid expected ACL")
    if not isinstance(value["entries"], str) or not value["entries"] or len(value["entries"]) > MAX_TOOL_OUTPUT:
        raise HelperError("Invalid expected ACL")


def require_fields(request, fields):
    if set(request) != fields:
        raise HelperError("Unexpected request fields")


def handle(request):
    root = read_root()
    action = request.get("action")
    if action == "snapshot":
        require_fields(request, {"action", "path"})
        parts = relative_parts(request["path"])
        validate_epub_or_temp(parts[-1])
        file_fd, info = open_file(root, parts)
        try:
            return snapshot(file_fd, info)
        finally:
            os.close(file_fd)

    if action != "copy-verify":
        raise HelperError("Unsupported action")
    require_fields(request, {"action", "source", "target", "expected"})
    source_parts = relative_parts(request["source"])
    target_parts = relative_parts(request["target"])
    if source_parts[-1].lower().endswith(".epub"):
        pass
    else:
        source_match = TEMP_NAME.fullmatch(source_parts[-1])
        if source_match is None:
            raise HelperError("ACL source must be an EPUB or operation backup")
    match = TEMP_NAME.fullmatch(target_parts[-1])
    if match is None or source_parts[:-1] != target_parts[:-1]:
        raise HelperError("ACL copies must target an operation file beside the EPUB")
    target_operation = target_parts[-1][len(".grimmory-"):-len(".candidate" if target_parts[-1].endswith(".candidate") else ".backup")]
    if not source_parts[-1].lower().endswith(".epub"):
        if source_parts[-1] != f".grimmory-{target_operation}.backup" \
                or not target_parts[-1].endswith(".candidate"):
            raise HelperError("Backup ACL copies must stay within one operation")
    validate_snapshot(request["expected"])
    expected = request["expected"]
    source_fd, source_info = open_file(root, source_parts)
    try:
        target_fd, target_info = open_file(root, target_parts)
        try:
            if target_info.st_nlink != 1 or (source_info.st_dev, source_info.st_ino) == \
                    (target_info.st_dev, target_info.st_ino):
                raise HelperError("ACL target must be an independent operation file")
            before = snapshot(source_fd, source_info)
            if before != expected:
                raise HelperError("Source ACL changed after inspection")

            os.fchown(target_fd, expected["uid"], expected["gid"])
            os.fchmod(target_fd, expected["mode"])
            run_tool("-copy", fd_path(source_fd), fd_path(target_fd), pass_fds=(source_fd, target_fd))

            target_after = snapshot(target_fd, os.fstat(target_fd))
            source_after = snapshot(source_fd, os.fstat(source_fd))
            if target_after != expected or source_after != expected:
                raise HelperError("ACL copy did not preserve the inspected attributes")
            return target_after
        finally:
            os.close(target_fd)
    finally:
        os.close(source_fd)


def main():
    try:
        if len(sys.argv) != 1:
            raise HelperError("Arguments are not accepted")
        response = json.dumps(handle(read_request()), separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        if len(response) > MAX_RESPONSE:
            raise HelperError("Response is too large")
        sys.stdout.buffer.write(response + b"\n")
        return 0
    except (HelperError, OSError, subprocess.SubprocessError, UnicodeError, ValueError):
        return fail()


if __name__ == "__main__":
    raise SystemExit(main())
