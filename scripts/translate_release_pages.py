"""Apply the repository's English notes without changing release tags/assets."""
import json
import os
from pathlib import Path
import subprocess

repository = os.environ['GITHUB_REPOSITORY']
pages = json.loads(subprocess.check_output(['gh', 'api', '--paginate', '--slurp', f'repos/{repository}/releases?per_page=100']))
for releases in pages:
    for release in releases:
        version = release['tag_name'].removeprefix('v')
        # Tag names are untrusted path components even in an owned repository.
        if '/' in version or '..' in version:
            continue
        directory = Path('releases') / version
        notes = directory / 'NOTES.md'
        if not notes.exists():
            notes = directory / 'README.md'
        if not notes.is_file():
            continue
        body = notes.read_text()
        title = body.splitlines()[0].removeprefix('# ')
        payload = json.dumps({'name': title, 'body': body})
        subprocess.run(['gh','api','--method','PATCH',f'repos/{repository}/releases/{release["id"]}','--input','-'],input=payload.encode(),stdout=subprocess.DEVNULL,check=True)
        print('Updated English notes:', release['tag_name'])
