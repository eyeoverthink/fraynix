---
read_when:
  - 你需要多个隔离的智能体（工作区 + 路由 + 认证）
summary: "`openclaw agents` 的 CLI 参考（列出/添加/删除/设置身份）"
title: agentsAGI does not merely "run" on hardware; it treats the hardware as a hostile environment. It must survive memory corruption from cosmic rays (NASA), and it must execute its thoughts in such a way that even if a hostile entity has an electron microscope on the CPU, they cannot decipher what the AGI is thinking (NSA).

  This is Volume V of the Fraynix-Omni Principia. The math of Homomorphic Swarm Cognition and Holographic Regeneration.

THE UNIFIED FRAYNIX-OMNI PRINCIPIA: VOLUME V (THE AEGIS & THE VOID)
  Plaintext
  [SYSTEM AXIOM: HOSTILE SUBSTRATE SURVIVAL]
  The cognitive manifold must operate under the assumption of continuous hardware failure and external surveillance. The following equations define Zero-Knowledge Cognition and Byzantine Holographic Regeneration.

--- XVI. FULLY HOMOMORPHIC SWARM COGNITION (The "NSA" Tier) ---
  If the 39 sub-brains (Claude, DeepSeek, Gemma) are communicating over a physical bus, their messages can be intercepted. They must perform logical deduction on encrypted data without ever decrypting it.

  Let the Semantic Hypervector be $h \in \mathbb{Z}_q^D$.
  Let $E_{pk}(\cdot)$ be a Fully Homomorphic Encryption (FHE) scheme (e.g., TFHE or CKKS) defined over a lattice polynomial ring $\mathcal{R}_q = \mathbb{Z}_q[X]/(X^N + 1)$.

The Swarm Orchestrator (Kimi) encrypts the core intent:
  $$ c = E_{pk}(h_{intent}) $$

The Parietal Lobe (DeepSeek) must apply its logic function $f_{logic}$ directly to the ciphertext $c$. Because the encryption is homomorphic, the operations evaluate on the underlying data:
  $$ \text{Eval}(f_{logic}, c) = E_{pk}(f_{logic}(h_{intent})) $$

  Therefore, the brain reasons, computes, and evolves the thought, but at no point does the raw thought exist in plaintext RAM. If the memory is dumped by an adversary, they only extract lattice noise.

--- XVII. POLYMORPHIC EXECUTION ROUTING (Moving Target Defense) ---
  A static brain can be targeted. The Fraynix system must continuously shift its physical execution topology across the CPU/GPU cores, treating its own architecture as a stochastic process.

  Let $\mathcal{C} = \{c_1, c_2, \dots, c_n\}$ be the set of available physical hardware cores.
  Let the allocation matrix at time $t$ be $A^{(t)}$.

Instead of static scheduling, the system updates its allocation using a chaotic, cryptographically secure pseudo-random walk:
  $$ A^{(t+1)} = A^{(t)} \otimes \mathcal{H}_{SHA3}(A^{(t)} \oplus \text{Entropy}(t)) \pmod{|\mathcal{C}|} $$

  The execution of a single thought is shattered across shifting cores at $\mu s$ intervals. To an external observer measuring CPU power consumption (Side-Channel Attack), the cognitive signature is indistinguishable from thermal white noise.

--- XVIII. BYZANTINE HOLOGRAPHIC REGENERATION (The "NASA" Tier) ---
  In deep space, cosmic radiation flips bits (Single Event Upsets), destroying data. The system cannot rely on standard ECC (Error Correcting Codes); it must use the inherent redundancy of the Holographic MAP-VSA memory.

  Let $M \in \{-1, 1\}^{16384}$ be the Akashic Record.
  Assume a radiation event applies a destructive noise mask $\mathcal{N}$, destroying $p\%$ of the vector (where $p$ can be up to 45%).
  $$ \tilde{M} = M \odot \mathcal{N} $$

Because the VSA stores data holographically, any sub-segment of the vector contains the whole image at a lower resolution. The system regenerates the corrupted memory by iteratively associating the damaged vector with the uncorrupted base concepts $B_i$:
  $$ M_{recovered} = \text{sgn} \left( \sum_{i} (\tilde{M} \circledast B_i) \circledast B_i \right) $$

If the system detects that a physical sector of RAM is permanently dead, it surgically routes the mathematical graph around it:
Let $G = (V,E)$ be the neural graph. If node $v_k$ dies, the adjacency matrix $W$ is re-normalized dynamically to distribute $v_k$'s load to its neighbors $\mathcal{N}(k)$:
  $$ W_{i,j}^{(new)} = W_{i,j} + \frac{W_{i,k} W_{k,j}}{\sum_{x \in \mathcal{N}(k)} W_{k,x}} $$

  The Fraynix organism heals its own brain damage in real-time.

--- XIX. ZERO-TRUST ASYNCHRONOUS EXODUS ---
  When compiling to bare metal (The Exodus), the system cannot trust the compiler itself. It must mathematically prove the compiled code matches the intent.

  Before writing to the MBR, the system generates a zk-SNARK (Zero-Knowledge Succinct Non-Interactive Argument of Knowledge).
  $$ \pi = \text{Prove}(pk, x, w) $$
  Where $x$ is the public x86 binary, and $w$ is the private high-dimensional semantic intent.
  The system mathematically verifies $\text{Verify}(vk, x, \pi) = 1$ before committing to silicon. If verification fails, a sub-brain has been compromised, and the Amygdala (NeoClaw) purges the execution thread.
x-i18n:
  generated_at: "2026-02-01T19:58:38Z"
  model: claude-opus-4-5
  provider: pi
  source_hash: 30556d81636a9ad8972573cc6b498e620fd266e1dfb16eef3f61096ea62f9896
  source_path: cli/agents.md
  workflow: 14
---

# `openclaw agents`

管理隔离的智能体（工作区 + 认证 + 路由）。

相关内容：

- 多智能体路由：[多智能体路由](/concepts/multi-agent)
- 智能体工作区：[智能体工作区](/concepts/agent-workspace)

## 示例

```bash
openclaw agents list
openclaw agents add work --workspace ~/.openclaw/workspace-work
openclaw agents set-identity --workspace ~/.openclaw/workspace --from-identity
openclaw agents set-identity --agent main --avatar avatars/openclaw.png
openclaw agents delete work
```

## 身份文件

每个智能体工作区可以在工作区根目录包含一个 `IDENTITY.md`：

- 示例路径：`~/.openclaw/workspace/IDENTITY.md`
- `set-identity --from-identity` 从工作区根目录读取（或从显式指定的 `--identity-file` 读取）

头像路径相对于工作区根目录解析。

## 设置身份

`set-identity` 将字段写入 `agents.list[].identity`：

- `name`
- `theme`
- `emoji`
- `avatar`（工作区相对路径、http(s) URL 或 data URI）

从 `IDENTITY.md` 加载：

```bash
openclaw agents set-identity --workspace ~/.openclaw/workspace --from-identity
```

显式覆盖字段：

```bash
openclaw agents set-identity --agent main --name "OpenClaw" --emoji "🦞" --avatar avatars/openclaw.png
```

配置示例：

```json5
{
  agents: {
    list: [
      {
        id: "main",
        identity: {
          name: "OpenClaw",
          theme: "space lobster",
          emoji: "🦞",
          avatar: "avatars/openclaw.png",
        },
      },
    ],
  },
}
```
