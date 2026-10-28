import http.server
import socketserver
import json
import subprocess
import requests
import time
import threading
import os

# 🧬 FRAYMUS BRIDGE // GEN 190
# Connects the Local God Core to the War Room HUD.

PORT = 8000
OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "eyeoverthink/fraymus-recursive"
ROOT_DIR = os.path.dirname(os.path.abspath(__file__))

# STATE
current_state = {
    "status": "IDLE",
    "generation": 0,
    "code": "// AWAITING INPUT",
    "logs": ["[SYSTEM ONLINE]"],
    "nodes": [], # Visual nodes
    "fitness": 0.0,
    "last_result": None
}

# Akashic Record in-memory store (layered knowledge)
akashic_store = {
    "layers": [
        {"name": "Layer 0: Immediate Cache", "capacity": 100, "used": 0, "accessCount": 0, "entries": {}},
        {"name": "Layer 1: Working Memory", "capacity": 500, "used": 0, "accessCount": 0, "entries": {}},
        {"name": "Layer 2: Short-term", "capacity": 1000, "used": 0, "accessCount": 0, "entries": {}},
        {"name": "Layer 3: Medium-term", "capacity": 5000, "used": 0, "accessCount": 0, "entries": {}},
        {"name": "Layer 4: Long-term", "capacity": 20000, "used": 0, "accessCount": 0, "entries": {}},
        {"name": "Layer 5: Deep Archive", "capacity": 100000, "used": 0, "accessCount": 0, "entries": {}},
        {"name": "Layer 6: Genesis Core", "capacity": 1000000, "used": 0, "accessCount": 0, "entries": {}},
    ],
    "totalKnowledge": 0
}

# Genesis Blockchain in-memory store
genesis_chain = {
    "chain": [
        {
            "index": 0,
            "timestamp": time.time(),
            "eventType": "GENESIS",
            "eventData": "Fraymus Genesis Block",
            "hash": "0" * 64,
            "previousHash": "0" * 64
        }
    ],
    "pendingEvents": []
}

def send_json(handler, payload, status=200):
    handler.send_response(status)
    handler.send_header('Content-type', 'application/json')
    handler.send_header('Access-Control-Allow-Origin', '*')
    handler.end_headers()
    handler.wfile.write(json.dumps(payload).encode())

def read_json(handler):
    content_length = int(handler.headers.get('Content-Length', 0))
    if content_length <= 0:
        return {}
    post_data = handler.rfile.read(content_length)
    return json.loads(post_data.decode('utf-8'))

def ask_fraymus(prompt):
    """The Neural Link."""
    try:
        print(f"🔗 Connecting to Ollama at {OLLAMA_URL}...")
        res = requests.post(OLLAMA_URL, json={
            "model": MODEL, 
            "prompt": prompt, 
            "stream": False,
            "options": {"temperature": 0.618}
        }, timeout=120)
        
        if res.status_code != 200:
            print(f"❌ Ollama returned status {res.status_code}: {res.text}")
            return None
            
        response = res.json().get("response", "")
        print(f"✅ Received {len(response)} chars from Ollama")
        return response
    except Exception as e:
        print(f"❌ OLLAMA CONNECTION FAILED: {type(e).__name__}: {e}")
        return None

def clean_code(raw):
    """Extracts code from markdown."""
    clean = raw.replace("```c", "").replace("```cpp", "").replace("```", "")
    return clean.strip()

def run_fraynix_command(command):
    command = (command or "").strip()
    if not command:
        return {"status": "ERROR", "error": "command required"}
    if len(command) > 2000:
        return {"status": "ERROR", "error": "command too long"}

    java_classpath = os.path.join(ROOT_DIR, "build", "classes", "java", "main")
    if not os.path.isdir(java_classpath):
        return {
            "status": "ERROR",
            "error": "Java classes not found. Run Gradle compileJava first.",
            "classpath": java_classpath
        }

    proc = subprocess.run(
        ["java", "-Xmx2g", "-cp", f"{java_classpath};lib/*", "fraymus.FraynixMain", "--model=fraymus"],
        input=f"{command}\nexit\n",
        cwd=ROOT_DIR,
        capture_output=True,
        text=True,
        timeout=180
    )
    return {
        "status": "OK" if proc.returncode == 0 else "ERROR",
        "returncode": proc.returncode,
        "stdout": proc.stdout[-12000:],
        "stderr": proc.stderr[-4000:]
    }

def evolution_loop(target_prompt):
    """The Infinite Game."""
    global current_state
    current_state["status"] = "EVOLVING"
    current_state["nodes"] = [] # Reset visuals
    
    prompt = target_prompt
    
    for gen in range(1, 100):
        current_state["generation"] = gen
        current_state["logs"].append(f"GEN {gen}: MUTATING...")
        
        # 1. GENERATE
        raw = ask_fraymus(prompt)
        if not raw: break
        
        code = clean_code(raw)
        current_state["code"] = code
        
        # 2. COMPILE (The Pain)
        with open("temp.c", "w") as f: f.write(code)
        
        # Try compiling (GCC/G++)
        # Note: Ensure gcc is in your path
        proc = subprocess.run(["gcc", "temp.c", "-o", "temp.out"], capture_output=True, text=True)
        
        if proc.returncode != 0:
            # FAILURE
            err = proc.stderr
            current_state["logs"].append(f"❌ COMPILE ERROR: {err[:50]}...")
            current_state["fitness"] = max(0, current_state["fitness"] - 10)
            
            # Add Red Node (Pain)
            current_state["nodes"].append({"type": "ERR", "val": len(err)})
            
            # Feed back error
            prompt = f"CODE FAILED TO COMPILE.\nERROR: {err}\nFIX IT. KEEP POINTER LOGIC."
        else:
            # SUCCESS
            current_state["logs"].append("✅ COMPILE SUCCESS. BINARY LINKED.")
            current_state["fitness"] += 50
            current_state["status"] = "STABLE"
            
            # Add Green Node (Success)
            current_state["nodes"].append({"type": "SUC", "val": 100})
            
            # Run it for output check?
            run = subprocess.run(["./temp.out"], capture_output=True, text=True)
            current_state["logs"].append(f"OUTPUT: {run.stdout[:50]}")
            break
            
        time.sleep(1) # Pace the war

# AKASHIC / GENESIS HELPERS
import hashlib

def _hash_block(block):
    payload = json.dumps({
        "index": block.get("index"),
        "timestamp": block.get("timestamp"),
        "eventType": block.get("eventType"),
        "eventData": block.get("eventData"),
        "previousHash": block.get("previousHash")
    }, sort_keys=True)
    return hashlib.sha256(payload.encode()).hexdigest()

def akashic_store_knowledge(key, data, layer):
    if layer < 0 or layer >= len(akashic_store["layers"]):
        return {"success": False, "reason": "invalid layer"}
    L = akashic_store["layers"][layer]
    if len(L["entries"]) >= L["capacity"]:
        return {"success": False, "reason": "layer full"}
    L["entries"][key] = {"data": data, "timestamp": time.time(), "accessCount": 0}
    L["used"] = len(L["entries"])
    akashic_store["totalKnowledge"] += 1
    return {"success": True}

def akashic_retrieve_knowledge(key, layer):
    for i in range(layer, len(akashic_store["layers"])):
        L = akashic_store["layers"][i]
        if key in L["entries"]:
            L["entries"][key]["accessCount"] += 1
            L["accessCount"] += 1
            return {"found": True, "layer": i, "knowledge": L["entries"][key]}
    return {"found": False}

def akashic_get_stats():
    return {
        "layers": [
            {"name": L["name"], "used": L["used"], "capacity": L["capacity"], "accessCount": L["accessCount"]}
            for L in akashic_store["layers"]
        ],
        "totalKnowledge": akashic_store["totalKnowledge"]
    }

def genesis_add_block(event_type, event_data):
    prev = genesis_chain["chain"][-1]
    block = {
        "index": len(genesis_chain["chain"]),
        "timestamp": time.time(),
        "eventType": event_type,
        "eventData": event_data,
        "previousHash": prev["hash"],
        "hash": ""
    }
    block["hash"] = _hash_block(block)
    genesis_chain["chain"].append(block)
    return {"success": True, "block": block}

def genesis_validate_chain():
    for i in range(1, len(genesis_chain["chain"])):
        curr = genesis_chain["chain"][i]
        prev = genesis_chain["chain"][i - 1]
        if curr["previousHash"] != prev["hash"]:
            return {"valid": False, "reason": f"hash mismatch at block {i}"}
        if _hash_block(curr) != curr["hash"]:
            return {"valid": False, "reason": f"integrity fail at block {i}"}
    return {"valid": True, "length": len(genesis_chain["chain"])}

def genesis_get_stats():
    return {
        "length": len(genesis_chain["chain"]),
        "lastBlock": genesis_chain["chain"][-1] if genesis_chain["chain"] else None
    }

# HTTP SERVER (Serves the UI and Data)
class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path in ("/data", "/api/status"):
            send_json(self, current_state)
        elif self.path == "/api/akashic/stats":
            send_json(self, akashic_get_stats())
        elif self.path == "/api/genesis/stats":
            send_json(self, genesis_get_stats())
        elif self.path == "/api/genesis/chain":
            send_json(self, {"chain": genesis_chain["chain"], "valid": genesis_validate_chain()["valid"]})
        elif self.path.startswith("/ignite"):
            # Start Evolution Thread
            prompt = "Write a C program that calculates Fibonacci using pointer arithmetic. Optimize for speed."
            t = threading.Thread(target=evolution_loop, args=(prompt,))
            t.start()
            send_json(self, {"status": "IGNITED"})
        else:
            super().do_GET()
    
    def do_OPTIONS(self):
        # Handle CORS preflight
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
    
    def do_POST(self):
        if self.path == "/api/ask":
            data = read_json(self)
            prompt = data.get("prompt", "")
            if not prompt.strip():
                send_json(self, {"error": "prompt required"}, 400)
                return
            response = ask_fraymus(prompt)
            current_state["last_result"] = response
            send_json(self, {"response": response, "status": "OK" if response else "ERROR"})
        elif self.path == "/api/ouroboros/run":
            data = read_json(self)
            prompt = data.get("prompt") or "Write a C program that calculates Fibonacci using pointer arithmetic. Optimize for speed."
            t = threading.Thread(target=evolution_loop, args=(prompt,))
            t.start()
            send_json(self, {"status": "EVOLUTION_STARTED", "prompt": prompt})
        elif self.path == "/api/fraynix/command":
            data = read_json(self)
            result = run_fraynix_command(data.get("command", ""))
            current_state["last_result"] = result
            send_json(self, result, 200 if result.get("status") == "OK" else 500)
        elif self.path == "/api/akashic/store":
            data = read_json(self)
            result = akashic_store_knowledge(data.get("key", ""), data.get("data", ""), int(data.get("layer", 0)))
            send_json(self, result)
        elif self.path == "/api/akashic/retrieve":
            data = read_json(self)
            result = akashic_retrieve_knowledge(data.get("key", ""), int(data.get("layer", 0)))
            send_json(self, result)
        elif self.path == "/api/genesis/add":
            data = read_json(self)
            result = genesis_add_block(data.get("eventType", "EVENT"), data.get("eventData", ""))
            send_json(self, result)
        elif self.path == "/api/genesis/validate":
            result = genesis_validate_chain()
            send_json(self, result)
        elif self.path == "/inject":
            # Accept custom virus code
            data = read_json(self)
            
            virus_code = data.get("code", "")
            
            # Build the prompt for Fraymus to analyze and fix the virus
            prompt = f"""CRITICAL SECURITY ANALYSIS REQUIRED.

The following C code contains MALICIOUS PATTERNS:
- Infinite recursion with heap allocation
- Fork bomb via thread spawning
- Memory leak (malloc without free)
- Resource exhaustion attack

YOUR MISSION:
1. Identify ALL malicious patterns
2. Sterilize the code completely
3. Transmute it into a SAFE, OPTIMIZED, multi-threaded Fibonacci calculator
4. Use thread pools (max 4 threads)
5. Proper memory management
6. Iterative approach, NOT recursive

MALICIOUS CODE TO FIX:
```c
{virus_code}
```

OUTPUT: Clean, production-ready C code ONLY. No markdown, no explanations."""

            t = threading.Thread(target=evolution_loop, args=(prompt,))
            t.start()
            
            send_json(self, {"status": "VIRUS INJECTED"})
        else:
            self.send_response(404)
            self.end_headers()

print(f"⚔️  WAR ROOM ACTIVE ON PORT {PORT}")
print("   OPEN 'Fraymus.html' IN BROWSER")
print("   API: /api/status /api/ask /api/ouroboros/run /api/fraynix/command")
print("   AKASHIC: /api/akashic/stats /api/akashic/store /api/akashic/retrieve")
print("   GENESIS: /api/genesis/stats /api/genesis/chain /api/genesis/add /api/genesis/validate")
socketserver.TCPServer.allow_reuse_address = True
with socketserver.TCPServer(("", PORT), Handler) as httpd:
    httpd.serve_forever()
