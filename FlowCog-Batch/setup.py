#!/usr/bin/env python
# -*- coding: utf-8 -*-

from setuptools import find_packages
from setuptools import setup

setup(
    name="flowcog-batch",
    version='0.1',
    author="XcDu",
    author_email="czduxuechao@gmail.com",
    packages=find_packages(),
    package_data={
        "": ["*.txt", "*.jar"]
    },
    include_package_data=True,
    entry_points={
        "console_scripts": [
            "flowcog_batch=flowcog_batch.main:main"
        ]
    }

)
