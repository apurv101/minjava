#!/usr/bin/env python3
import os

def print_tree(startpath, prefix=""):
    # List and sort the items in the current directory
    items = sorted(os.listdir(startpath))
    count = len(items)
    for index, item in enumerate(items):
        path = os.path.join(startpath, item)
        # Choose the connector based on whether it's the last item
        if index == count - 1:
            connector = "└── "
            new_prefix = prefix + "    "
        else:
            connector = "├── "
            new_prefix = prefix + "│   "
        print(prefix + connector + item)
        # If the item is a directory, recursively print its content
        if os.path.isdir(path):
            print_tree(path, new_prefix)

if __name__ == "__main__":
    # Set the root directory here (current directory is used by default)
    root_dir = "."
    print(root_dir)
    print_tree(root_dir)
