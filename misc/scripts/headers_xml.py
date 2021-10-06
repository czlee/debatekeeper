"""Updates the copyright notices in XML files.

This is just intended to help automate things a little. It's not intended to be run without manually
editing some results."""

from pathlib import Path
import re
import subprocess

base_path = Path("../app/src/main/res")

original = r"""<!--
 \* Copyright \(C\) ([\d\-]+)\s+([\w\-, ]+)
 \*
 \* This file is part of the Debatekeeper app, which is licensed under the
 \* GNU General Public Licence version 3 \(GPLv3\)\.  You can redistribute
 \* and/or modify it under the terms of the GPLv3, and you must not use
 \* this file except in compliance with the GPLv3\.
 \*
 \* This app is distributed in the hope that it will be useful, but
 \* WITHOUT ANY WARRANTY; without even the implied warranty of
 \* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE\.  See the
 \* GNU General Public Licence for more details\.
 \*
 \* You should have received a copy of the GNU General Public Licence
 \* along with this program\.  If not, see <http://www\.gnu\.org/licenses/>\.
-->
"""

replacement = """
<!--
  Copyright (C) {copyright_details}

  This file is part of the Debatekeeper app, which is licensed under the GNU General Public Licence
  version 3 (GPLv3).  You can redistribute and/or modify it under the terms of the GPLv3, and you
  must not use this file except in compliance with the GPLv3.

  This app is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General
  Public Licence for more details.

  You should have received a copy of the GNU General Public Licence along with this program.  If
  not, see <http://www.gnu.org/licenses/>.
-->
"""


def get_year(path):
    output = subprocess.check_output(["git", "log", "--follow", "--diff-filter=A", "--", path]).decode()
    m = re.search(r"\d\d:\d\d:\d\d (\d{4})", output)
    if not m:
        return "<unknown>"
    return m.group(1)


for subdir in base_path.iterdir():

    if subdir.name in ["values-de", "values-fr", "values-es"]:
        continue
    if subdir.name.startswith("drawable"):
        continue
    if not subdir.is_dir():
        continue

    for filepath in subdir.iterdir():

        if filepath.suffix != ".xml":
            continue

        with open(filepath) as f:
            contents = f.read()

        parts = re.split(original, contents)
        history_year = get_year(filepath)

        assert len(parts) in [1, 4]

        if len(parts) == 4:
            header, year, authors, body = parts
            summary = "\033[1;32m" + year + " " + authors + '\033[0m'
            if history_year != year:
                summary += " \033[1;31m" + history_year + "\033[0m"
            copyright_details = year + " " + authors

        else:
            header, body = parts[0].split('\n', maxsplit=1)
            summary = "\033[1;31mnot found " + history_year + "\033[0m"
            copyright_details = history_year + " Chuan-Zheng Lee"

        shortpath = f"{filepath.parent.name}/{filepath.name}"
        print(f"{shortpath}: {summary}")

        new_contents = header.strip() + replacement.format(copyright_details=copyright_details) + body

        with open(filepath, "w") as f:
            f.write(new_contents)
