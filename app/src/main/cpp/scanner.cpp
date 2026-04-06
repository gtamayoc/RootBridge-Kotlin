/**
 * scanner.cpp — RootBridge NDK Memory Scanner
 *
 * Commands:
 *   scan           <pid> <needle_hex>              — Full scan, saves session to SESSION_FILE
 *   filter_exact   <pid> <needle_hex>              — Keep only addrs matching exact new value
 *   filter_changed <pid>                           — Keep only addrs whose value CHANGED vs stored
 *   filter_unchanged <pid>                         — Keep only addrs whose value is SAME vs stored
 *   print_results  <limit>                         — Print first N results from current session
 *   clear_session                                  — Delete session file
 *
 * Session file format (binary, little-endian):
 *   [uint32_t needle_size]                         — stored per-entry needle byte-size
 *   [Entry × N]:
 *     [uint64_t address]
 *     [uint8_t  value_bytes × needle_size]         — value snapshot at scan time
 */

#include <iostream>
#include <fstream>
#include <sstream>
#include <vector>
#include <string>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>

using namespace std;

// ────────────────────────────────────────────────────────────────────────────
// Constants
// ────────────────────────────────────────────────────────────────────────────
static const char* SESSION_FILE = "/data/local/tmp/rootbridge_session.bin";
static const size_t CHUNK_SIZE  = 4 * 1024 * 1024; // 4 MB

// ────────────────────────────────────────────────────────────────────────────
// Structures
// ────────────────────────────────────────────────────────────────────────────
struct MemEntry {
    uint64_t address;
    vector<uint8_t> value; // snapshot value at time of scan/filter
};

struct MemoryRegion {
    uintptr_t start;
    uintptr_t end;
};

// ────────────────────────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────────────────────────
vector<uint8_t> hexToBytes(const string& hex) {
    vector<uint8_t> bytes;
    for (size_t i = 0; i + 1 < hex.size(); i += 2) {
        bytes.push_back((uint8_t)strtol(hex.substr(i, 2).c_str(), nullptr, 16));
    }
    return bytes;
}

bool isTargetRegion(const string& line) {
    if (line.find("rw-p") == string::npos) return false;
    if (line.find("kgsl") != string::npos) return false;
    if (line.find("/memfd") != string::npos) return false;

    if (line.find("[anon:") != string::npos ||
        line.find("[heap]") != string::npos ||
        line.find(".dex")   != string::npos ||
        line.find("[stack") != string::npos) return true;

    // Unnamed anonymous pages
    size_t inodeEnd = line.find_last_not_of(" \t\r\n");
    if (inodeEnd != string::npos &&
        line.find(" /")  == string::npos &&
        line.find("[")   == string::npos) return true;

    return false;
}

vector<MemoryRegion> parseMaps(int pid) {
    vector<MemoryRegion> regions;
    string path = "/proc/" + to_string(pid) + "/maps";
    ifstream file(path);
    if (!file.is_open()) return regions;

    string line;
    while (getline(file, line)) {
        if (!isTargetRegion(line)) continue;
        size_t dash  = line.find('-');
        size_t space = line.find(' ');
        if (dash == string::npos || space == string::npos) continue;

        uintptr_t start = strtoull(line.substr(0, dash).c_str(), nullptr, 16);
        uintptr_t end   = strtoull(line.substr(dash + 1, space - dash - 1).c_str(), nullptr, 16);
        if (end > start && (end - start) <= (size_t)(128 * 1024 * 1024)) {
            regions.push_back({start, end});
        }
    }
    return regions;
}

// ────────────────────────────────────────────────────────────────────────────
// Session I/O
// ────────────────────────────────────────────────────────────────────────────
bool writeSession(const vector<MemEntry>& entries, uint32_t needle_size) {
    FILE* f = fopen(SESSION_FILE, "wb");
    if (!f) return false;

    fwrite(&needle_size, sizeof(uint32_t), 1, f);
    for (const auto& e : entries) {
        fwrite(&e.address, sizeof(uint64_t), 1, f);
        fwrite(e.value.data(), 1, needle_size, f);
    }
    fclose(f);
    return true;
}

bool readSession(vector<MemEntry>& out_entries, uint32_t& out_needle_size) {
    FILE* f = fopen(SESSION_FILE, "rb");
    if (!f) return false;

    if (fread(&out_needle_size, sizeof(uint32_t), 1, f) != 1 || out_needle_size == 0 || out_needle_size > 16) {
        fclose(f);
        return false;
    }

    MemEntry e;
    e.value.resize(out_needle_size);
    while (true) {
        if (fread(&e.address, sizeof(uint64_t), 1, f) != 1) break;
        if (fread(e.value.data(), 1, out_needle_size, f) != out_needle_size) break;
        out_entries.push_back(e);
    }
    fclose(f);
    return true;
}

// ────────────────────────────────────────────────────────────────────────────
// SCAN — Full sweep of memory, save all matches to session file
// ────────────────────────────────────────────────────────────────────────────
void cmdScan(int pid, const vector<uint8_t>& needle) {
    vector<MemoryRegion> regions = parseMaps(pid);
    if (regions.empty()) {
        cerr << "No readable regions found for PID " << pid << "\n";
        return;
    }

    string memPath = "/proc/" + to_string(pid) + "/mem";
    int fd = open(memPath.c_str(), O_RDONLY);
    if (fd < 0) {
        cerr << "Cannot open " << memPath << "\n";
        return;
    }

    uint8_t* buf = new uint8_t[CHUNK_SIZE];
    size_t needle_len = needle.size();
    vector<MemEntry> results;
    results.reserve(65536);

    for (const auto& region : regions) {
        uintptr_t cur = region.start;
        while (cur < region.end) {
            size_t toRead = (size_t)min((uintptr_t)CHUNK_SIZE, region.end - cur);
            ssize_t bytesRead = pread64(fd, buf, toRead, (off64_t)cur);
            if (bytesRead <= 0) break;

            for (ssize_t i = 0; i <= bytesRead - (ssize_t)needle_len; ++i) {
                if (memcmp(buf + i, needle.data(), needle_len) == 0) {
                    MemEntry e;
                    e.address = cur + i;
                    e.value.assign(needle.begin(), needle.end());
                    results.push_back(move(e));
                }
            }
            cur += bytesRead;
        }
    }

    delete[] buf;
    close(fd);

    writeSession(results, (uint32_t)needle_len);
    cout << "TOTAL_FOUND:" << results.size() << "\n";
}

// ────────────────────────────────────────────────────────────────────────────
// FILTER EXACT — Keep addresses whose current memory value == needle
// ────────────────────────────────────────────────────────────────────────────
void cmdFilterExact(int pid, const vector<uint8_t>& needle) {
    vector<MemEntry> entries;
    uint32_t needle_size = 0;
    if (!readSession(entries, needle_size)) {
        cerr << "No session to filter\n";
        return;
    }

    string memPath = "/proc/" + to_string(pid) + "/mem";
    int fd = open(memPath.c_str(), O_RDONLY);
    if (fd < 0) {
        cerr << "Cannot open " << memPath << "\n";
        return;
    }

    size_t nlen = needle.size();
    vector<uint8_t> buf(nlen);
    vector<MemEntry> kept;
    kept.reserve(entries.size() / 4);

    for (const auto& e : entries) {
        ssize_t r = pread64(fd, buf.data(), nlen, (off64_t)e.address);
        if (r == (ssize_t)nlen && memcmp(buf.data(), needle.data(), nlen) == 0) {
            MemEntry ne;
            ne.address = e.address;
            ne.value.assign(needle.begin(), needle.end()); // Update stored snapshot
            kept.push_back(move(ne));
        }
    }
    close(fd);

    writeSession(kept, (uint32_t)nlen);
    cout << "TOTAL_FOUND:" << kept.size() << "\n";
}

// ────────────────────────────────────────────────────────────────────────────
// FILTER CHANGED — Keep addresses whose value NOW != stored snapshot
// ────────────────────────────────────────────────────────────────────────────
void cmdFilterChanged(int pid) {
    vector<MemEntry> entries;
    uint32_t needle_size = 0;
    if (!readSession(entries, needle_size)) {
        cerr << "No session to filter\n";
        return;
    }

    string memPath = "/proc/" + to_string(pid) + "/mem";
    int fd = open(memPath.c_str(), O_RDONLY);
    if (fd < 0) {
        cerr << "Cannot open " << memPath << "\n";
        return;
    }

    vector<uint8_t> buf(needle_size);
    vector<MemEntry> kept;
    kept.reserve(entries.size() / 8);

    for (const auto& e : entries) {
        ssize_t r = pread64(fd, buf.data(), needle_size, (off64_t)e.address);
        if (r == (ssize_t)needle_size && memcmp(buf.data(), e.value.data(), needle_size) != 0) {
            // Value CHANGED — store new value as the snapshot for next filter iteration
            MemEntry ne;
            ne.address = e.address;
            ne.value.assign(buf.begin(), buf.end()); // Update to current value
            kept.push_back(move(ne));
        }
    }
    close(fd);

    writeSession(kept, needle_size);
    cout << "TOTAL_FOUND:" << kept.size() << "\n";
}

// ────────────────────────────────────────────────────────────────────────────
// FILTER UNCHANGED — Keep addresses whose value NOW == stored snapshot
// ────────────────────────────────────────────────────────────────────────────
void cmdFilterUnchanged(int pid) {
    vector<MemEntry> entries;
    uint32_t needle_size = 0;
    if (!readSession(entries, needle_size)) {
        cerr << "No session to filter\n";
        return;
    }

    string memPath = "/proc/" + to_string(pid) + "/mem";
    int fd = open(memPath.c_str(), O_RDONLY);
    if (fd < 0) {
        cerr << "Cannot open " << memPath << "\n";
        return;
    }

    vector<uint8_t> buf(needle_size);
    vector<MemEntry> kept;
    kept.reserve(entries.size() / 2);

    for (const auto& e : entries) {
        ssize_t r = pread64(fd, buf.data(), needle_size, (off64_t)e.address);
        if (r == (ssize_t)needle_size && memcmp(buf.data(), e.value.data(), needle_size) == 0) {
            // Value unchanged — keep snapshot as-is
            kept.push_back(e);
        }
    }
    close(fd);

    writeSession(kept, needle_size);
    cout << "TOTAL_FOUND:" << kept.size() << "\n";
}

// ────────────────────────────────────────────────────────────────────────────
// PRINT RESULTS — Print first N entries from session (for UI display)
// ────────────────────────────────────────────────────────────────────────────
void cmdPrintResults(size_t limit) {
    vector<MemEntry> entries;
    uint32_t needle_size = 0;
    if (!readSession(entries, needle_size) || entries.empty()) {
        cout << "TOTAL_FOUND:0\n";
        return;
    }

    size_t count = (limit == 0 || limit > entries.size()) ? entries.size() : limit;
    for (size_t i = 0; i < count; ++i) {
        // Output: address,hex_value_bytes
        cout << entries[i].address;
        for (uint8_t b : entries[i].value) {
            char tmp[3];
            snprintf(tmp, sizeof(tmp), "%02x", b);
            cout << "," << tmp;
        }
        cout << "\n";
    }
    cout << "TOTAL_FOUND:" << entries.size() << "\n";
}

// ────────────────────────────────────────────────────────────────────────────
// CLEAR SESSION
// ────────────────────────────────────────────────────────────────────────────
void cmdClearSession() {
    remove(SESSION_FILE);
    cout << "SESSION_CLEARED\n";
}

// ────────────────────────────────────────────────────────────────────────────
// MAIN
// ────────────────────────────────────────────────────────────────────────────
int main(int argc, char* argv[]) {
    if (argc < 2) {
        cerr << "Usage: mem_scanner <command> [args...]\n"
             << "  scan           <pid> <needle_hex>\n"
             << "  filter_exact   <pid> <needle_hex>\n"
             << "  filter_changed <pid>\n"
             << "  filter_unchanged <pid>\n"
             << "  print_results  <limit>\n"
             << "  clear_session\n";
        return 1;
    }

    string cmd = argv[1];

    if (cmd == "scan") {
        if (argc < 4) { cerr << "scan requires <pid> <needle_hex>\n"; return 1; }
        cmdScan(atoi(argv[2]), hexToBytes(argv[3]));

    } else if (cmd == "filter_exact") {
        if (argc < 4) { cerr << "filter_exact requires <pid> <needle_hex>\n"; return 1; }
        cmdFilterExact(atoi(argv[2]), hexToBytes(argv[3]));

    } else if (cmd == "filter_changed") {
        if (argc < 3) { cerr << "filter_changed requires <pid>\n"; return 1; }
        cmdFilterChanged(atoi(argv[2]));

    } else if (cmd == "filter_unchanged") {
        if (argc < 3) { cerr << "filter_unchanged requires <pid>\n"; return 1; }
        cmdFilterUnchanged(atoi(argv[2]));

    } else if (cmd == "print_results") {
        size_t limit = (argc >= 3) ? (size_t)atoll(argv[2]) : 500;
        cmdPrintResults(limit);

    } else if (cmd == "clear_session") {
        cmdClearSession();

    } else {
        cerr << "Unknown command: " << cmd << "\n";
        return 1;
    }

    return 0;
}
