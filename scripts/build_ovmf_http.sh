#!/usr/bin/env bash
# Build a test firmware with native HTTP Boot. Does not flash a physical PC.
set -euo pipefail
task_root="$(cd "$(dirname "$0")/.." && pwd)"
task_work="${1:-$task_root/lab-output/edk2}"
for tool in git make gcc g++ nasm iasl python3; do
    command -v "$tool" >/dev/null || { echo "Missing build tool: $tool" >&2; exit 1; }
done
if [[ ! -d "$task_work/.git" ]]; then
    git clone --depth 1 --branch edk2-stable202605 https://github.com/tianocore/edk2.git "$task_work"
fi
cd "$task_work"
# Only the dependencies used by this firmware/tools, not unit-test frameworks.
git submodule update --init --depth 1 \
    BaseTools/Source/C/BrotliCompress/brotli \
    MdeModulePkg/Library/BrotliCustomDecompressLib/brotli \
    MdePkg/Library/BaseFdtLib/libfdt MdePkg/Library/MipiSysTLib/mipisyst \
    CryptoPkg/Library/OpensslLib/openssl CryptoPkg/Library/MbedTlsLib/mbedtls \
    SecurityPkg/DeviceSecurity/SpdmLib/libspdm TcgTpmPkg/Library/TpmLib/TPM \
    MdeModulePkg/Universal/RegularExpressionDxe/oniguruma RedfishPkg/Library/JsonLib/jansson
make -C BaseTools -j "$(getconf _NPROCESSORS_ONLN)"
export EDK_TOOLS_PATH="$task_work/BaseTools"
set +u
source edksetup.sh
set -u
build -a X64 -t GCC -b RELEASE -p OvmfPkg/OvmfPkgX64.dsc \
    -D NETWORK_HTTP_BOOT_ENABLE=TRUE -D NETWORK_TLS_ENABLE=FALSE \
    -D NETWORK_ALLOW_HTTP_CONNECTIONS=TRUE \
    -D FD_SIZE_4MB -n "$(getconf _NPROCESSORS_ONLN)"
echo "HTTP-enabled test firmware: $task_work/Build/OvmfX64/RELEASE_GCC/FV/OVMF_CODE.fd"
echo "Use the matching OVMF_VARS.fd from the same build; Secure Boot is not enabled."
