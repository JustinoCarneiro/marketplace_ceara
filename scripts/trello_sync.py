#!/usr/bin/env python3
import urllib.request, urllib.parse, json, argparse, sys, os

KEY = os.environ.get("TRELLO_KEY")
TOKEN = os.environ.get("TRELLO_TOKEN")

if not KEY or not TOKEN:
    print(
        "Erro: defina TRELLO_KEY e TRELLO_TOKEN como variáveis de ambiente antes de rodar este script.\n"
        "Gere em https://trello.com/power-ups/admin (API key) e via authorize (token).",
        file=sys.stderr,
    )
    sys.exit(1)

BOARDS = {
    "Sistema Melvin": "66b4ff4d0e142a30aed91cbb",
    "Marketplace Ceará": "63beccde337fc800bbcf45ee",
    "Sistema Lucas": "69951af0ee6e12883ce5aac2",
    "SAW HUB": "6a6ba95ff873a38adf05ff8f",
    "Heliene Macedo": "6a831529c1f77f8bdbb26789",
}

def req(method, url, data=None):
    url += f"&key={KEY}&token={TOKEN}" if "?" in url else f"?key={KEY}&token={TOKEN}"
    if data:
        data = urllib.parse.urlencode(data).encode("utf-8")
    try:
        req_obj = urllib.request.Request(url, data=data, method=method)
        with urllib.request.urlopen(req_obj) as r:
            return json.loads(r.read().decode()) if r.length else {}
    except urllib.error.HTTPError as e:
        print(f"Erro HTTP: {e.code} - {e.read().decode()}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Erro: {str(e)}", file=sys.stderr)
        sys.exit(1)

def get_list_by_name(board_id, name):
    lists = req("GET", f"https://api.trello.com/1/boards/{board_id}/lists")
    for lst in lists:
        if name.lower() in lst["name"].lower():
            return lst["id"]
    return None

def cmd_create(args):
    board_id = BOARDS.get(args.board)
    if not board_id:
        print(f"Board '{args.board}' não encontrado.", file=sys.stderr)
        sys.exit(1)

    list_id = get_list_by_name(board_id, args.list or "Backlog")
    if not list_id:
        print("Lista não encontrada.", file=sys.stderr)
        sys.exit(1)

    data = {"name": args.name, "idList": list_id}
    if args.desc:
        data["desc"] = args.desc

    res = req("POST", "https://api.trello.com/1/cards", data)
    print(f"Card criado com sucesso. ID: {res.get('id')}")

def cmd_update(args):
    data = {}
    if args.name: data["name"] = args.name
    if args.desc: data["desc"] = args.desc

    if not data:
        print("Nenhum dado fornecido para atualizar.", file=sys.stderr)
        sys.exit(1)

    res = req("PUT", f"https://api.trello.com/1/cards/{args.card_id}", data)
    print(f"Card {args.card_id} atualizado com sucesso.")

def cmd_delete(args):
    req("DELETE", f"https://api.trello.com/1/cards/{args.card_id}")
    print(f"Card {args.card_id} deletado com sucesso.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Trello Sync CLI for Onda Methodology")
    subparsers = parser.add_subparsers(dest="command", required=True)

    p_create = subparsers.add_parser("create")
    p_create.add_argument("--board", required=True, choices=list(BOARDS.keys()))
    p_create.add_argument("--name", required=True)
    p_create.add_argument("--desc", help="Card description (spec context)")
    p_create.add_argument("--list", help="List name (default: Backlog)")

    p_update = subparsers.add_parser("update")
    p_update.add_argument("--card-id", required=True)
    p_update.add_argument("--name", help="New name")
    p_update.add_argument("--desc", help="New description")

    p_delete = subparsers.add_parser("delete")
    p_delete.add_argument("--card-id", required=True)

    args = parser.parse_args()

    if args.command == "create": cmd_create(args)
    elif args.command == "update": cmd_update(args)
    elif args.command == "delete": cmd_delete(args)
