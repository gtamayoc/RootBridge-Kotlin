#include <iostream>
#include <fstream>
#include <sstream>
#include <vector>
#include <string>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <cstdint>
#include <cstdlib>

using namespace std;

// Maximum chunk size for memory reads
const size_t CHUNK_SIZE = 1024 * 1024 * 4; // 4 MB chunk
const size_t MAX_RESULTS = 1000000;

struct MemoryRegion {
    uintptr_t start;
    uintptr_t end;
};

// Convert Little-Endian Hex String to Byte Vector
vector<uint8_t> hexToBytes(const string& hex) {
    vector<uint8_t> bytes;
    for (size_t i = 0; i < hex.length(); i += 2) {
        string byteString = hex.substr(i, 2);
        uint8_t byte = (uint8_t) strtol(byteString.c_str(), NULL, 16);
        bytes.push_back(byte);
    }
    return bytes;
}

// Check if a line from /proc/pid/maps belongs strictly to the app (anon, heap, apk/dex, no huge graphics)
bool isTargetRegion(const string& line) {
    if (line.find("rw-p") == string::npos) return false;
    
    // Ignore massive zero-named or kgsl buffers commonly not part of regular memory
    if (line.find("kgsl") != string::npos) return false;
    
    if (line.find("[anon:") != string::npos ||
        line.find("[heap]") != string::npos ||
        line.find(".so") != string::npos ||
        line.find(".dex") != string::npos ||
        line.find("[stack") != string::npos) {
        return true;
    }
    
    // Also include anonymous regions without any name (common for malloc)
    // The format is usually "address perms offset dev inode     pathname"
    // If pathname is entirely empty
    int inodePos = line.find_last_not_of(" \t\r\n");
    if (inodePos != string::npos && line.find(" /") == string::npos && line.find("[") == string::npos) {
        // It might be an unnamed region
        return true;
    }
    return false;
}

vector<MemoryRegion> parseMaps(int pid) {
    vector<MemoryRegion> regions;
    string path = "/proc/" + to_string(pid) + "/maps";
    ifstream file(path);
    string line;
    
    while (getline(file, line)) {
        if (!isTargetRegion(line)) continue;
        
        size_t dash = line.find('-');
        size_t space = line.find(' ');
        if (dash != string::npos && space != string::npos) {
            uintptr_t start = strtoull(line.substr(0, dash).c_str(), NULL, 16);
            uintptr_t end = strtoull(line.substr(dash + 1, space - dash - 1).c_str(), NULL, 16);
            // Skip regions larger than 128 MB (usually system buffers/graphics)
            if (end - start <= 128 * 1024 * 1024) {
                regions.push_back({start, end});
            }
        }
    }
    return regions;
}

void scanMemory(int pid, const vector<uint8_t>& needle) {
    vector<MemoryRegion> regions = parseMaps(pid);
    if (regions.empty()) return;
    
    string memPath = "/proc/" + to_string(pid) + "/mem";
    int fd = open(memPath.c_str(), O_RDONLY);
    if (fd < 0) return;
    
    uint8_t* buffer = new uint8_t[CHUNK_SIZE];
    size_t needle_len = needle.size();
    
    size_t totalFound = 0;

    for (const auto& region : regions) {
        uintptr_t current = region.start;
        while (current < region.end && totalFound < MAX_RESULTS) {
            size_t toRead = std::min((size_t)(region.end - current), CHUNK_SIZE);
            
            ssize_t bytesRead = pread64(fd, buffer, toRead, current);
            if (bytesRead <= 0) break; // EOF or error reading region

            // Scan chunk memory natively
            for (ssize_t i = 0; i <= bytesRead - (ssize_t)needle_len; i += 1) { // 1 byte alignment step
                if (memcmp(buffer + i, needle.data(), needle_len) == 0) {
                    uintptr_t matchAddr = current + i;
                    // Print exclusively raw addresses, separated by newline
                    cout << matchAddr << "\n";
                    totalFound++;
                }
            }
            current += bytesRead;
        }
        if (totalFound >= MAX_RESULTS) break;
    }
    
    delete[] buffer;
    close(fd);
}

void filterMemory(int pid, const vector<uint8_t>& needle) {
    string memPath = "/proc/" + to_string(pid) + "/mem";
    int fd = open(memPath.c_str(), O_RDONLY);
    if (fd < 0) return;
    
    uint8_t buffer[16]; // Usually needles are 1-8 bytes
    size_t needle_len = needle.size();
    
    string line;
    // Read previous addresses from stdin
    while (getline(cin, line)) {
        if (line.empty()) continue;
        uintptr_t addr = stoull(line);
        
        ssize_t bytesRead = pread64(fd, buffer, needle_len, addr);
        if (bytesRead == (ssize_t)needle_len) {
            if (memcmp(buffer, needle.data(), needle_len) == 0) {
                cout << addr << "\n";
            }
        }
    }
    close(fd);
}

int main(int argc, char* argv[]) {
    if (argc < 4) {
        cerr << "Usage: mem_scanner [scan|filter] [pid] [hex_needle]" << endl;
        return 1;
    }

    string mode = argv[1];
    int pid = atoi(argv[2]);
    vector<uint8_t> needle = hexToBytes(argv[3]);

    if (mode == "scan") {
        scanMemory(pid, needle);
    } else if (mode == "filter") {
        filterMemory(pid, needle);
    }

    return 0;
}
