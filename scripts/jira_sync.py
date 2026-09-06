#!/usr/bin/env python3
import urllib.request, urllib.parse, json, argparse, sys, os, base64

JIRA_URL = os.environ.get("JIRA_URL")
JIRA_EMAIL = os.environ.get("JIRA_EMAIL")
JIRA_API_TOKEN = os.environ.get("JIRA_API_TOKEN")

if not JIRA_URL or not JIRA_EMAIL or not JIRA_API_TOKEN:
    print(
        "Erro: defina JIRA_URL, JIRA_EMAIL e JIRA_API_TOKEN como variáveis de ambiente antes de rodar este script.\n"
        "Gere o token em https://id.atlassian.com/manage-profile/security/api-tokens",
        file=sys.stderr,
    )
    sys.exit(1)

PROJECTS = {
    "Sistema Melvin": "MEL",
    "Marketplace Ceará": "MKT",
    "Sistema Lucas": "LUC",
    "SAW HUB": "SAW",
    "Heliene Macedo": "HEL",
}

ISSUE_TYPES = {"epic": "Epic", "story": "Story", "task": "Task", "bug": "Bug"}

def _auth_header():
    token = base64.b64encode(f"{JIRA_EMAIL}:{JIRA_API_TOKEN}".encode()).decode()
    return f"Basic {token}"

def req(method, path, data=None):
    url = f"{JIRA_URL}{path}"
    headers = {
        "Authorization": _auth_header(),
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    body = json.dumps(data).encode("utf-8") if data is not None else None
    try:
        req_obj = urllib.request.Request(url, data=body, method=method, headers=headers)
        with urllib.request.urlopen(req_obj) as r:
            raw = r.read()
            return json.loads(raw.decode()) if raw else {}
    except urllib.error.HTTPError as e:
        print(f"Erro HTTP: {e.code} - {e.read().decode()}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Erro: {str(e)}", file=sys.stderr)
        sys.exit(1)

def _to_adf(text):
    paragraphs = text.split("\n\n") if text else [""]
    return {
        "type": "doc",
        "version": 1,
        "content": [
            {
                "type": "paragraph",
                "content": [{"type": "text", "text": p}] if p else [],
            }
            for p in paragraphs
        ],
    }

def cmd_create(args):
    project_key = PROJECTS.get(args.project)
    if not project_key:
        print(f"Projeto '{args.project}' não encontrado.", file=sys.stderr)
        sys.exit(1)

    fields = {
        "project": {"key": project_key},
        "summary": args.name,
        "issuetype": {"name": ISSUE_TYPES.get(args.type, "Task")},
    }
    if args.desc:
        fields["description"] = _to_adf(args.desc)

    res = req("POST", "/rest/api/3/issue", {"fields": fields})
    print(f"Issue criada com sucesso: {res.get('key')}")

def cmd_update(args):
    fields = {}
    if args.name:
        fields["summary"] = args.name
    if args.desc:
        fields["description"] = _to_adf(args.desc)

    if not fields:
        print("Nenhum dado fornecido para atualizar.", file=sys.stderr)
        sys.exit(1)

    req("PUT", f"/rest/api/3/issue/{args.issue_key}", {"fields": fields})
    print(f"Issue {args.issue_key} atualizada com sucesso.")

def cmd_delete(args):
    req("DELETE", f"/rest/api/3/issue/{args.issue_key}")
    print(f"Issue {args.issue_key} deletada com sucesso.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Jira Sync CLI for Onda Methodology")
    subparsers = parser.add_subparsers(dest="command", required=True)

    p_create = subparsers.add_parser("create")
    p_create.add_argument("--project", required=True, choices=list(PROJECTS.keys()))
    p_create.add_argument("--name", required=True)
    p_create.add_argument("--desc", help="Issue description (spec context)")
    p_create.add_argument("--type", default="task", choices=list(ISSUE_TYPES.keys()), help="Tipo de issue (default: task)")

    p_update = subparsers.add_parser("update")
    p_update.add_argument("--issue-key", required=True)
    p_update.add_argument("--name", help="Novo summary")
    p_update.add_argument("--desc", help="Nova description")

    p_delete = subparsers.add_parser("delete")
    p_delete.add_argument("--issue-key", required=True)

    args = parser.parse_args()

    if args.command == "create": cmd_create(args)
    elif args.command == "update": cmd_update(args)
    elif args.command == "delete": cmd_delete(args)
