from flask import Flask, request, render_template_string
from datetime import datetime
from collections import deque
import os

app = Flask(__name__)

crashes = deque(maxlen=50)
LOG_FILE = "crash_log.txt"

HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <title>Crash Logs</title>
    <style>
        body { font-family: Consolas, monospace; background: #1e1e1e; color: #eee; padding: 20px; }
        h1 { color: #6cf; }
        .crash { background: #2b2b2b; padding: 10px 15px; border-radius: 8px; margin-bottom: 15px; white-space: pre-wrap; }
        .time { color: #aaa; font-size: 0.9em; margin-bottom: 8px; display: block; }
        .divider { border-bottom: 1px solid #444; margin: 20px 0; }
    </style>
</head>
<body>
    <h1>latest crashes</h1>
    {% if crashes %}
        {% for c in crashes %}
            <div class="crash">
                <span class="time"> {{ c["time"] }}</span>
                {{ c["data"] }}
            </div>
        {% endfor %}
    {% else %}
        <p>no crashes yet</p>
    {% endif %}
    <div class="divider"></div>
    <p>host: {{ host }} | last update: {{ now }}</p>
</body>
</html>
"""

@app.route("/")
def index():
    return render_template_string(
        HTML_TEMPLATE,
        crashes=reversed(crashes),
        host=request.host,
        now=datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    )

@app.route("/upload_log", methods=["POST"])
def upload_log():
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    if "logfile" in request.files:
        content = request.files["logfile"].read().decode(errors="ignore")
    else:
        content = request.get_data(as_text=True).strip()

    log_entry = f"[{timestamp}]\n{content}\n{'='*60}\n"
    print(f"\n=== Crash received at {timestamp} ===\n{content}\n=== End of crash ===\n")

    # сохраняем в память и в файл
    crashes.append({"time": timestamp, "data": content})
    with open(LOG_FILE, "a", encoding="utf-8") as f:
        f.write(log_entry)

    return "OK", 200


if __name__ == "__main__":
    os.makedirs(os.path.dirname(LOG_FILE) or ".", exist_ok=True)
    app.run(host="0.0.0.0", port=5000)
