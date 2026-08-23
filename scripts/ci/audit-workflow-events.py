#!/usr/bin/env python3
"""audit-workflow-events.py — static workflow event policy audit for Woodpecker.

Scans .woodpecker/*.{yml,yaml} and validates each workflow's when.event against
the platform policy (CRON IS INFRASTRUCTURE-ONLY).

Exit: 0 = pass, 1 = violations, 2 = tooling error.
"""
import os
import re
import sys

def _load_yaml():
    try:
        import yaml
        return yaml
    except ImportError:
        pass
    here = os.path.dirname(os.path.abspath(__file__))
    for cand in (os.path.join(here, "vendor", "yaml"),
                 os.path.join(here, "..", "vendor", "yaml")):
        p = os.path.abspath(cand)
        if os.path.isdir(p) and os.path.isfile(os.path.join(p, "__init__.py")):
            sys.path.insert(0, os.path.dirname(p))
            try:
                import yaml
                return yaml
            except ImportError:
                sys.path.pop(0)
    return None

YAML = _load_yaml()
if YAML is None:
    print("ERROR: no YAML parser available (need PyYAML or vendored yaml/)")
    print("WORKFLOW_EVENT_POLICY_PASS=NO")
    sys.exit(2)

POLICY = {
    "mirror-sync": {"require": ["cron"], "forbid": ["push", "pull_request"], "warn": ["manual"]},
    "verify":     {"require": [], "forbid": ["cron"]},
    "release":    {"require": ["deployment"], "forbid": ["cron", "push", "pull_request"], "warn": ["manual"]},
    "bench":      {"require": ["manual"], "forbid": ["cron", "push", "pull_request"]},
}
# semantic aliases: root .woodpecker.yml is stored as workflow "woodpecker" and is
# the repo's default pipeline (verify-equivalent)
SEMANTIC_ALIAS = {"woodpecker": "verify"}
SEVERITY = {
    ("release", "cron"): "CRITICAL", ("verify", "cron"): "HIGH",
    ("release", "push"): "HIGH", ("release", "pull_request"): "HIGH",
    ("bench", "cron"): "MEDIUM", ("bench", "push"): "MEDIUM", ("bench", "pull_request"): "MEDIUM",
    ("mirror-sync", "push"): "MEDIUM", ("mirror-sync", "pull_request"): "MEDIUM",
}

def sev(wf, ev):
    return SEVERITY.get((wf, ev), "HIGH")

def parse_events(node):
    if not isinstance(node, dict):
        return []
    when = node.get("when")
    if isinstance(when, list):
        evs = []
        for cond in when:
            if isinstance(cond, dict):
                e = cond.get("event")
                evs.extend(str(x) for x in e) if isinstance(e, list) else (evs.append(str(e)) if e else None)
        return evs
    if isinstance(when, dict):
        e = when.get("event")
        if isinstance(e, list):
            return [str(x) for x in e]
        return [str(e)] if e else []
    return []

def main():
    args = sys.argv[1:]
    scan_dir, repo, i = ".woodpecker", "", 0
    while i < len(args):
        if args[i] == "--dir" and i + 1 < len(args): scan_dir = args[i+1]; i += 2
        elif args[i] == "--repo" and i + 1 < len(args): repo = args[i+1]; i += 2
        else: i += 1
    if not os.path.isdir(scan_dir):
        print("ERROR: scan dir not found: %s" % scan_dir); print("WORKFLOW_EVENT_POLICY_PASS=NO"); sys.exit(2)
    files = sorted(f for f in os.listdir(scan_dir) if re.match(r".+\.(ya?ml)$", f, re.I))
    if not files:
        print("ERROR: no workflow files in %s" % scan_dir); print("WORKFLOW_EVENT_POLICY_PASS=NO"); sys.exit(2)
    prefix = ("[%s] " % repo) if repo else ""
    failures = 0
    for fname in files:
        path = os.path.join(scan_dir, fname)
        wf = os.path.splitext(fname)[0]
        try:
            with open(path, "r", encoding="utf-8") as fh:
                node = YAML.safe_load(fh)
        except Exception as exc:
            print("%sFAIL %s: YAML parse error (%s)" % (prefix, fname, exc)); failures += 1; continue
        if not isinstance(node, dict):
            print("%sFAIL %s: invalid workflow" % (prefix, fname)); failures += 1; continue
        events = parse_events(node)
        eventset = set(events)
        wf = SEMANTIC_ALIAS.get(wf, wf)
        pol = POLICY.get(wf)
        if pol is None:
            print("%sINFO %s: no policy entry (events=%s)" % (prefix, fname, ",".join(sorted(eventset)) or "none"))
            continue
        for req in pol.get("require", []):
            if req in eventset:
                print("%sPASS %s: %s present" % (prefix, fname, req))
            else:
                lvl = "HIGH" if wf == "release" else "MEDIUM"
                print("%sFAIL %s: required event=%s missing (severity=%s)" % (prefix, fname, req, lvl)); failures += 1
        for forb in pol.get("forbid", []):
            if forb in eventset:
                print("%sFAIL %s: forbidden event=%s (severity=%s)" % (prefix, fname, forb, sev(wf, forb))); failures += 1
            else:
                print("%sPASS %s: %s absent" % (prefix, fname, forb))
        for warn in pol.get("warn", []):
            if warn in eventset:
                print("%sINFO %s: event=%s present (note policy)" % (prefix, fname, warn))
        if wf == "release":
            if node.get("require") or node.get("approval"):
                print("%sWARN %s: 'require/approval' key NOT supported by Woodpecker 3.17 (native gate = deployment)" % (prefix, fname))
            if "deployment" not in eventset:
                print("%sFAIL %s: approval gate missing (event=deployment required, severity=HIGH)" % (prefix, fname)); failures += 1
            else:
                print("%sPASS %s: approval gate present (deployment)" % (prefix, fname))
    print()
    if failures == 0:
        print("WORKFLOW_EVENT_POLICY_PASS=YES"); sys.exit(0)
    print("WORKFLOW_EVENT_POLICY_PASS=NO"); sys.exit(1)

if __name__ == "__main__":
    main()
