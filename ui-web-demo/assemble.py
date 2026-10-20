import os
import sys

# Add ui-web-demo to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from parts_css import CSS_BLOCK
from parts_html import HTML_BODY
from parts_js import JS_BLOCK

html = f"""<!DOCTYPE html>
<html lang="zh-CN" data-theme="dark">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
  <title>ClashMetaX UI 2.0 - 交互式网页原型系统</title>
  <style>
{CSS_BLOCK}
  </style>
</head>
<body>
{HTML_BODY}
  <script>
{JS_BLOCK}
  </script>
</body>
</html>
"""

output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "index.html")
with open(output_path, "w", encoding="utf-8") as f:
    f.write(html)

print(f"Assembly complete! Written {len(html)} characters to {output_path}")
