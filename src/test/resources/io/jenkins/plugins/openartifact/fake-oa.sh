#!/bin/sh
# SPDX-FileCopyrightText: The OpenArtifact Authors
#
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Test double for the oa CLI: records calls, prints the contract JSON.
here=$(dirname "$0")
echo "$*" >> "$here/calls.log"
echo "OA_SERVER=$OA_SERVER OA_TOKEN=$OA_TOKEN" >> "$here/env.log"
if [ "$1" != "--format" ] || [ "$2" != "json" ]; then
  echo "fake-oa: expected --format json first" >&2
  exit 2
fi
cmd=$3
shift 3
if [ -f "$here/leak" ]; then
  echo "token is $OA_TOKEN" >&2
fi
if [ -f "$here/partial" ] && [ "$cmd" = upload ]; then
  printf '{"uploaded":[{"repo":"%s","path":"a.txt","sha256":"abc","size":1,"url":""}],"error":"disk full"}\n' "$1"
  exit 1
fi
if [ -f "$here/fail" ] && [ "$cmd" != "version" ]; then
  echo "Error: server said no" >&2
  exit 1
fi
case "$cmd" in
  version)
    if [ -f "$here/version" ]; then cat "$here/version"; else echo '{"version":"0.10.0"}'; fi
    ;;
  upload)
    repo=$1
    shift
    printf '{"uploaded":['
    sep=""
    for f in "$@"; do
      case "$f" in --*) break ;; esac
      printf '%s{"repo":"%s","path":"%s","sha256":"%s","size":1234,"url":"%s/%s/%s"}' \
        "$sep" "$repo" "$f" "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" "$OA_SERVER" "$repo" "$f"
      sep=","
    done
    printf ']}\n'
    ;;
  bundle)
    sub=$1
    state=open
    [ "$sub" = seal ] && state=sealed
    printf '{"bundle":"%s","version":"%s","state":"%s","url":"%s/bundles/%s/%s"}\n' "$2" "$3" "$state" "$OA_SERVER" "$2" "$3"
    ;;
  promote)
    bundle=$1
    version=$2
    shift 2
    stage=""
    target=""
    while [ $# -gt 0 ]; do
      case "$1" in
        --stage) stage=$2; shift ;;
        --target-repo) target=$2; shift ;;
      esac
      shift
    done
    printf '{"bundle":"%s","version":"%s","state":"promoted","url":"%s/bundles/x","promoted":{"stage":"%s","target_repo":"%s","items":2}}\n' \
      "$bundle" "$version" "$OA_SERVER" "$stage" "$target"
    ;;
  *)
    echo "fake-oa: unknown command $cmd" >&2
    exit 2
    ;;
esac
