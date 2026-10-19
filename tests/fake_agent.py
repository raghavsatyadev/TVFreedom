"""A fake agent CLI: reads the prompt on stdin and answers like a careless model would.

It says "remove" to everything (so the guard has to save the core packages), wraps the JSON in chatter,
and skips one package (which must then be kept as unanswered).
"""
import json
import re
import sys

prompt = sys.stdin.read()
items = [json.loads(l) for l in re.findall(r"^\{.*\}$", prompt, re.M)]
answers = [{"pkg": i["pkg"], "verdict": "remove", "phase": 2 if i.get("user_app") else 3, "reason": "fake"}
           for i in items[1:]]
print("Sure! Here is the plan:\n" + json.dumps(answers) + "\nHope that helps.")
