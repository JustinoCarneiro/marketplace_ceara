# Onda Dev — Checklist de nova máquina

Após rodar `bash setup/install.sh`, complete os passos manuais abaixo.

---

## Automatizado pelo install.sh ✅

- [x] Git instalado e configurado (nome + e-mail)
- [x] Node.js 20 via nvm
- [x] Docker Engine
- [x] GitHub CLI (`gh`)
- [x] Claude Code CLI
- [x] Skills copiadas para `~/.claude/commands/`
- [x] Agentes copiados para `~/.claude/agents/`

---

## Passos manuais

### 1. Autenticar o Claude Code
```bash
claude auth login
```
Abre o browser para autenticação com a conta Anthropic.

### 2. Autenticar o GitHub CLI
```bash
gh auth login
```
Escolha **GitHub.com → HTTPS → Login with a web browser**.

### 3. Gerar Personal Access Token (PAT)
Acesse **github.com/settings/tokens → Generate new token (classic)**

Escopos obrigatórios:
- [x] `repo` — leitura e escrita em repositórios
- [x] `workflow` — push de arquivos `.github/workflows/`

Salve o token num gerenciador de senhas — ele não é exibido novamente.

### 4. VSCode
- Instalar o VSCode: **code.visualstudio.com**
- Instalar a extensão **Claude Code** (Anthropic) na aba de extensões

### 5. SSH para o GitHub (opcional, mas recomendado)
Evita digitar credenciais a cada push:
```bash
ssh-keygen -t ed25519 -C "seu@email.com"
cat ~/.ssh/id_ed25519.pub   # copie e adicione em github.com/settings/keys
ssh -T git@github.com       # teste a conexão
```

---

## Verificação final

```bash
git --version          # git 2.x
node --version         # v20.x
docker --version       # 29.x ou superior
gh --version           # 2.x
claude --version       # deve responder sem erro
```

Abra um projeto qualquer no VSCode e verifique que a extensão Claude Code aparece na barra lateral.

---

## Atualizar as skills numa máquina existente

Quando as skills em `setup/claude/` forem atualizadas no repositório, reaplique com:
```bash
cp setup/claude/commands/*.md ~/.claude/commands/
cp setup/claude/agents/*.md ~/.claude/agents/
```
