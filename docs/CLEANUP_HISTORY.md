# Repository History Cleanup Guide

This guide provides step-by-step instructions for safely removing large files from the repository history and migrating to Git LFS.

## ⚠️ Important Warnings

**Before you begin:**

1. **Coordinate with all team members** - History rewriting requires force-pushing and will affect all collaborators
2. **Backup your repository** - Clone a backup copy before starting
3. **Timing** - Choose a time when no one else is actively working on the repository
4. **Communication** - Notify all collaborators that they will need to re-clone or reset their local repositories

## Prerequisites

Install the required tools:

### Option 1: BFG Repo-Cleaner (Recommended for beginners)

```bash
# macOS (Homebrew)
brew install bfg

# Or download directly
wget https://repo1.maven.org/maven2/com/madgag/bfg/1.14.0/bfg-1.14.0.jar
# Then use: java -jar bfg-1.14.0.jar
```

### Option 2: git-filter-repo (More powerful, recommended for advanced users)

```bash
# macOS (Homebrew)
brew install git-filter-repo

# Linux (pip)
pip install git-filter-repo

# Or download from: https://github.com/newren/git-filter-repo
```

### Git LFS

```bash
# macOS (Homebrew)
brew install git-lfs

# Linux (Debian/Ubuntu)
sudo apt-get install git-lfs

# Initialize Git LFS
git lfs install
```

## Step 1: Identify Large Files

Run the provided script to find large files:

```bash
./scripts/find-big-files.sh
```

This will show you the largest files in your repository. Make a list of files to remove from history.

You can also find files in history that may no longer exist in the working tree:

```bash
# Find large files in Git history
git rev-list --objects --all | \
  git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' | \
  sed -n 's/^blob //p' | \
  sort --numeric-sort --key=2 --reverse | \
  head -20 | \
  cut -d ' ' -f 2- | \
  awk '{print $1, $2}' | \
  numfmt --field=1 --to=iec-i --suffix=B
```

## Step 2: Backup Your Repository

```bash
# Create a complete backup
cd /path/to/your/repo
cd ..
git clone --mirror vericrop-miniproject vericrop-miniproject-backup.git
```

## Step 3: Choose Your Cleanup Method

### Method A: Remove Specific Large Files (BFG)

If you know which files to remove:

```bash
# Clone a fresh copy (bare)
git clone --mirror https://github.com/imperfectperson-max/vericrop-miniproject.git

cd vericrop-miniproject.git

# Remove files by name
java -jar bfg.jar --delete-files "vericrop_quality_model_scripted.pt"

# Or remove files larger than 5MB
java -jar bfg.jar --strip-blobs-bigger-than 5M

# Clean up
git reflog expire --expire=now --all
git gc --prune=now --aggressive

# Force push (⚠️ Requires admin access and team coordination)
git push --force
```

### Method B: Remove Specific Large Files (git-filter-repo)

More precise control with git-filter-repo:

```bash
# Clone a fresh copy
git clone https://github.com/imperfectperson-max/vericrop-miniproject.git
cd vericrop-miniproject

# Remove specific files from history
git filter-repo --path docker/ml-service/model/vericrop_quality_model_scripted.pt --invert-paths
git filter-repo --path docker/ml-service/ml-models/vericrop_quality_model_scripted.pt --invert-paths

# Or remove files by size (removes files larger than 5MB)
git filter-repo --strip-blobs-bigger-than 5M

# Force push (⚠️ Requires admin access and team coordination)
git remote add origin https://github.com/imperfectperson-max/vericrop-miniproject.git
git push --force --all
git push --force --tags
```

### Method C: Remove Files by Pattern

```bash
# Using BFG
java -jar bfg.jar --delete-files "*.pt"  # Remove all .pt files
java -jar bfg.jar --delete-files "*.h5"  # Remove all .h5 files

# Using git-filter-repo
git filter-repo --path-glob '*.pt' --invert-paths
git filter-repo --path-glob '*.h5' --invert-paths
```

## Step 4: Migrate Large Files to Git LFS (Optional)

If you want to keep large files but manage them with Git LFS:

### 4.1: Track files with Git LFS

```bash
# Initialize Git LFS in your repository
git lfs install

# Track specific file types (already configured in .gitattributes)
# The .gitattributes file in this repo already includes common binary types

# Verify LFS tracking
git lfs track

# Expected output should show patterns from .gitattributes:
# *.pt
# *.png
# etc.
```

### 4.2: Migrate existing files to LFS

```bash
# For each large file you want to keep, migrate it:
git lfs migrate import --include="*.pt" --everything
git lfs migrate import --include="*.png" --everything
git lfs migrate import --include="*.h5" --everything

# Verify LFS objects
git lfs ls-files

# Push LFS objects
git push --force
```

### 4.3: Alternative - Manual migration

```bash
# Remove file from history first (using BFG or git-filter-repo)
# Then re-add it with LFS:

# Make sure the file pattern is in .gitattributes
echo "*.pt filter=lfs diff=lfs merge=lfs -text" >> .gitattributes

# Add the file back
cp /path/to/backup/large-file.pt ./path/in/repo/
git add .gitattributes path/in/repo/large-file.pt
git commit -m "Re-add large file with Git LFS"
git push
```

## Step 5: Verify the Cleanup

```bash
# Check repository size
du -sh .git

# Check for remaining large files
./scripts/find-big-files.sh

# Verify Git LFS files (if migrated)
git lfs ls-files
```

## Step 6: Force Push and Notify Team

```bash
# Push the cleaned history (⚠️ DESTRUCTIVE - requires coordination)
git push --force --all
git push --force --tags

# If using LFS, push LFS objects
git lfs push --all origin
```

## Step 7: Team Members Must Re-sync

After force-pushing, all team members must update their local repositories:

### Option 1: Fresh clone (Recommended)

```bash
# Backup local changes if any
cd ~/path/to/vericrop-miniproject
git diff > my-local-changes.patch

# Delete old repo and clone fresh
cd ..
rm -rf vericrop-miniproject
git clone https://github.com/imperfectperson-max/vericrop-miniproject.git
cd vericrop-miniproject

# Apply local changes if needed
git apply my-local-changes.patch
```

### Option 2: Hard reset (Advanced users only)

```bash
cd ~/path/to/vericrop-miniproject

# Backup uncommitted work
git stash

# Fetch the new history
git fetch origin --force

# Reset to match origin
git reset --hard origin/main

# Clean up
git for-each-ref --format='delete %(refname)' refs/original | git update-ref --stdin
git reflog expire --expire=now --all
git gc --prune=now --aggressive

# Restore work if needed
git stash pop
```

## Step 8: Set Up Prevention

The repository now includes prevention measures:

1. **.gitattributes** - Configures Git LFS for binary files
2. **.gitignore** - Prevents accidental commits of generated files
3. **GitHub Action** - Blocks PRs with files >5MB
4. **find-big-files.sh** - Script to monitor repository size

## Troubleshooting

### "Remote rejected (protected branch)"

- You need admin permissions to force-push to protected branches
- Temporarily disable branch protection in GitHub Settings → Branches
- Re-enable protection after cleanup

### "This exceeds GitHub's file size limit"

- Files larger than 100MB cannot be pushed to GitHub even after cleanup
- Use Git LFS for these files or store them externally

### "Git LFS quota exceeded"

- GitHub has LFS storage limits (1GB free, 50GB for Pro)
- Consider external storage (S3, CDN) for very large files
- Use GitHub Release assets for distributable binaries

### Team members having sync issues

- Ensure they completely re-clone or use hard reset
- Check that they have Git LFS installed if you're using it
- Verify they're pulling from the correct branch

## Alternative: Use Release Assets

For distributable binaries (models, executables), consider using GitHub Releases:

```bash
# Create a release with assets via GitHub CLI
gh release create v1.0.0 \
  --title "VeriCrop v1.0.0" \
  --notes "Release notes here" \
  docker/ml-service/model/vericrop_quality_model_scripted.pt

# Or upload manually via GitHub web interface:
# Repository → Releases → Draft a new release → Attach files
```

This keeps large files out of the repository entirely while still making them available.

## References

- [BFG Repo-Cleaner](https://rtyley.github.io/bfg-repo-cleaner/)
- [git-filter-repo](https://github.com/newren/git-filter-repo)
- [Git LFS](https://git-lfs.github.com/)
- [GitHub: Removing sensitive data](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
- [Atlassian: Git LFS Tutorial](https://www.atlassian.com/git/tutorials/git-lfs)

## Summary

1. ✅ Backup repository
2. ✅ Identify large files
3. ✅ Choose cleanup method (BFG or git-filter-repo)
4. ✅ Optionally migrate to Git LFS
5. ✅ Verify cleanup
6. ✅ Force push with team coordination
7. ✅ Team members re-clone or hard reset
8. ✅ Prevention measures in place

**Remember:** History rewriting is a one-way operation. Make sure you have backups and team coordination before proceeding.
