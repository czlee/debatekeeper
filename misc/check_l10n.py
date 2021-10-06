"""Checks that translated strings are in the correct files.

This only checks one way: It checks that translated strings are where we expect them to be. It
doesn't check that all translated strings are present.

This was mainly a tool to help split strings into appropriate files before uploading them to
Crowdin.
"""

import xml.etree.ElementTree as etree
from pathlib import Path

base_path = Path("../app/src/main/res")
source_path = base_path / "values"
languages = ["de", "es", "fr"]


def short(path):
    return f"{path.parent.name}/\033[1;33m{path.name}\033[0m"


def get_strings(path):
    """Returns the list of string elements in the file, or an empty list if there aren't any (for
    whatever reason). If there aren't any, prints a message explaining."""


    if path.suffix != ".xml":
        print(f"{short(path)}: not an XML file, skipping")
        return []

    tree = etree.parse(path)
    root = tree.getroot()
    if root.tag != "resources":
        print(f"{short(path)}: root element was not <resources>, skipping")
        return []

    strings = root.findall("string") + root.findall("plurals") + root.findall("string-array")
    if len(strings) == 0:
        print(f"{short(path)}: no strings found, skipping")
        return []

    return strings


# Build a dictionary noting the location of each string

sources = {}

for path in source_path.iterdir():

    strings = get_strings(path)
    for string in strings:
        sources[string.get('name')] = path.name


# Check the strings in the translated files

for lang in languages:
    translations_path = base_path / f"values-{lang}"

    for path in translations_path.iterdir():

        strings = get_strings(path)

        for string in strings:
            name = string.get('name')
            if name not in sources:
                print(f"{short(path)}: unrecognized string {name}")
                continue

            expected_location = sources[name]
            if path.name != expected_location:
                print(f"{short(path)}: {string.tag} \033[1;36m{name}\033[0m should be "
                      f"in \033[1;32m{expected_location}\033[0m")

