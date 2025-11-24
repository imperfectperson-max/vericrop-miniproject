# Repository Size Management Guide

## Overview

This document explains why the VeriCrop repository may grow large over time and provides strategies for managing repository size effectively.

## Current Situation

As of the latest analysis:

- **Total repository size**: ~132MB
- **Git history size**: ~42MB
- **Working tree size**: ~90MB

### Major Contributors to Repository Size

1. **Machine Learning Models** (86MB total)
   - `docker/ml-service/model/vericrop_quality_model_scripted.pt` (43MB)
   - `docker/ml-service/ml-models/vericrop_quality_model_scripted.pt` (43MB)
   - These PyTorch models for crop quality assessment are binary files

2. **Images** (~1MB+)
   - `vericrop-gui/src/main/resources/images/vericrop-icon.png.png` (1MB)
   - Various sample images in `examples/` and `docker/ml-service/tests/data/`

3. **Java JAR files** 
   - Gradle wrapper JAR (~60KB)

4. **Documentation and code** (~30MB)
   - Extensive markdown documentation
   - Java and Python source code

## Why Repository Size Matters

Large repositories cause several problems:

1. **Slow Cloning**: First-time clones take longer, slowing developer onboarding
2. **CI/CD Performance**: Every CI run must clone the repository
3. **Developer Experience**: Git operations (fetch, pull, push) are slower
4. **Storage Costs**: More expensive in CI/CD minutes and storage
5. **Disk Space**: Large repos consume more local disk space

## Prevention Measures (Already Implemented)

This repository now includes several preventive measures:

### 1. Enhanced .gitignore

The `.gitignore` file now excludes:
- Build artifacts (`build/`, `target/`, `dist/`)
- Dependencies (`node_modules/`, `venv/`, `.gradle`)
- Generated files (reports, logs, temporary files)
- Large binary files that should use Git LFS

### 2. Git LFS Configuration (.gitattributes)

The `.gitattributes` file configures Git LFS for:
- Machine learning models (`.pt`, `.pth`, `.h5`, `.pkl`)
- Images (`.png`, `.jpg`, `.gif`, etc.)
- Videos and audio files
- Archives (`.zip`, `.tar.gz`, etc.)
- PDFs and other documents

**Note**: Files already in history are NOT automatically migrated to LFS. See [CLEANUP_HISTORY.md](./CLEANUP_HISTORY.md) for migration instructions.

### 3. Automated Large File Check

A GitHub Action (`.github/workflows/large-file-check.yml`) runs on every PR and push to:
- Detect files larger than 5MB
- Block PRs that add large files
- Provide guidance on alternatives

### 4. Monitoring Script

The `scripts/find-big-files.sh` script helps identify large files:

```bash
# Find the 20 largest files
./scripts/find-big-files.sh

# Find the 50 largest files over 1MB
./scripts/find-big-files.sh 50 1
```

Run this periodically to monitor repository size.

## Short-Term Mitigations

These measures are already active and will prevent new large files from being added. However, they don't affect files already in the repository history.

### What's Protected Now

✅ New large files will be blocked by CI  
✅ Build artifacts won't be accidentally committed  
✅ Generated files are ignored  
✅ Dependencies are excluded  
✅ LFS is configured for future migrations  

### What Still Needs Action

❌ Large files already in history (86MB+ of ML models)  
❌ Historical commits containing large files  
❌ Git object database hasn't been cleaned  

## Long-Term Solutions

To fully resolve repository size issues, consider these options:

### Option 1: Remove Large Files from History (Recommended)

**Impact**: Permanently removes files from all commits  
**Complexity**: Medium (requires coordination)  
**Size Reduction**: Significant (could reduce by 50-70%)

See [CLEANUP_HISTORY.md](./CLEANUP_HISTORY.md) for detailed instructions using:
- BFG Repo-Cleaner (easier)
- git-filter-repo (more control)

**Best for**: Files that aren't needed in history or can be regenerated

### Option 2: Migrate to Git LFS

**Impact**: Moves large files to LFS storage  
**Complexity**: Medium  
**Size Reduction**: Moderate (history still contains old versions)

Steps:
1. Install Git LFS
2. Configure tracking (already done in `.gitattributes`)
3. Migrate files: `git lfs migrate import --include="*.pt" --everything`
4. Push changes

**Best for**: Binary files that need version control but shouldn't bloat the repo

### Option 3: Use External Storage

**Impact**: Removes files entirely from repository  
**Complexity**: Varies (depends on infrastructure)  
**Size Reduction**: Maximum

Options:
- **Cloud Storage** (S3, GCS, Azure Blob)
  - Upload models to S3/GCS
  - Download at build time or via script
  - Use pre-signed URLs or CDN for access
  
- **GitHub Releases**
  - Attach files to release tags
  - Download specific versions as needed
  - Good for stable model checkpoints
  
- **DVC (Data Version Control)**
  - Specialized for ML projects
  - Versions data separately from code
  - Integrates with cloud storage

**Best for**: Files that don't need to be in the repository at all

### Option 4: Use an Assets Branch

**Impact**: Separates large files into a different branch  
**Complexity**: Low  
**Size Reduction**: Moderate (main branch is clean)

```bash
# Create an assets branch
git checkout --orphan assets
git rm -rf .
git add docker/ml-service/model/*.pt
git commit -m "Add ML model assets"
git push -u origin assets

# In main branch, download assets as needed
git checkout main
```

**Best for**: Release assets and binaries that need to be available but not in main history

## Recommended Approach for VeriCrop

Based on the analysis, here's the recommended strategy:

### Phase 1: Immediate (Already Done)
- ✅ Add `.gitattributes` for Git LFS
- ✅ Enhance `.gitignore` to prevent future bloat
- ✅ Add CI check to block large files
- ✅ Add monitoring script

### Phase 2: Near-term (Optional - Maintainer Decision)

Choose ONE of the following:

**Option A: Clean History + Git LFS** (Recommended)
1. Remove large files from history using BFG or git-filter-repo
2. Migrate necessary files to Git LFS
3. Force-push cleaned history
4. Team members re-clone repositories

**Option B: External Storage + Clean History**
1. Upload ML models to cloud storage (S3/GCS)
2. Remove models from repository entirely
3. Update build scripts to download models
4. Document model management in README

**Option C: Assets Branch**
1. Create separate `assets` branch for large binaries
2. Keep main branch lightweight
3. CI/CD downloads from assets branch as needed

### Phase 3: Ongoing
- Monitor repository size monthly using `find-big-files.sh`
- Review CI logs for large file warnings
- Educate team on binary file management
- Update processes for new model versions

## Best Practices Going Forward

### For ML Models

**DON'T**:
- ❌ Commit model checkpoints directly to main
- ❌ Keep multiple versions of large models
- ❌ Store training data in the repository

**DO**:
- ✅ Use Git LFS for versioned models
- ✅ Store models in cloud storage (S3/GCS)
- ✅ Use model registries (MLflow, Weights & Biases)
- ✅ Document model versions in text files
- ✅ Provide download scripts for models

### For Images and Media

**DON'T**:
- ❌ Commit high-resolution images
- ❌ Store multiple sizes of the same image
- ❌ Include test fixtures larger than 100KB

**DO**:
- ✅ Use optimized/compressed versions
- ✅ Leverage Git LFS for essential images
- ✅ Link to external CDN for assets
- ✅ Generate test fixtures at runtime

### For Build Artifacts

**DON'T**:
- ❌ Commit compiled binaries
- ❌ Include dependencies (node_modules, venv)
- ❌ Store build outputs

**DO**:
- ✅ Let CI build from source
- ✅ Use package managers for dependencies
- ✅ Distribute via GitHub Releases or artifact registries

### For Documentation

**DON'T**:
- ❌ Embed large images in markdown
- ❌ Include presentation files
- ❌ Store video tutorials in repo

**DO**:
- ✅ Use optimized images with external links
- ✅ Store presentations elsewhere (Google Slides, etc.)
- ✅ Link to video hosting (YouTube, Vimeo)

## Monitoring and Alerts

### Weekly Monitoring

Run the monitoring script:

```bash
./scripts/find-big-files.sh
```

Alert if:
- Any single file exceeds 5MB
- Total working tree exceeds 100MB
- .git directory exceeds 50MB

### CI/CD Monitoring

The GitHub Action automatically:
- Checks every PR for files >5MB
- Blocks merges with large files
- Comments with remediation steps

### Manual Checks

Periodically check repository size:

```bash
# Total size
du -sh .

# Git database size
du -sh .git

# Largest files in history
git rev-list --objects --all | \
  git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' | \
  sed -n 's/^blob //p' | \
  sort --numeric-sort --key=2 --reverse | \
  head -20
```

## FAQs

### Q: Why not just use Git LFS for everything?

**A**: Git LFS has costs and limitations:
- GitHub provides 1GB free storage and 1GB free bandwidth per month
- Additional storage: $5/month per 50GB
- Additional bandwidth: $5/month per 50GB
- LFS adds complexity to workflows
- Not all tools support LFS transparently

### Q: Will cleaning history break things?

**A**: If done correctly, no. But:
- All team members must re-clone or reset
- Old commit SHAs will change
- External references to commits will break
- Requires coordination and downtime

### Q: Can we undo a history rewrite?

**A**: Only if you have a backup. That's why backups are critical before any history rewrite.

### Q: Should we migrate existing large files to LFS?

**A**: Consider these factors:
- Do you need version history for these files?
- How often do they change?
- Can they be stored externally?
- Do you have LFS storage quota?

For ML models that rarely change, external storage is often better than LFS.

### Q: What's the ideal repository size?

**A**: General guidelines:
- **Excellent**: < 50MB
- **Good**: 50-100MB
- **Acceptable**: 100-500MB
- **Problem**: > 500MB

For repositories with ML models, external storage is recommended regardless.

## Resources

- [CLEANUP_HISTORY.md](./CLEANUP_HISTORY.md) - History cleanup guide
- [Git LFS Documentation](https://git-lfs.github.com/)
- [BFG Repo-Cleaner](https://rtyley.github.io/bfg-repo-cleaner/)
- [git-filter-repo](https://github.com/newren/git-filter-repo)
- [DVC (Data Version Control)](https://dvc.org/)
- [GitHub: Repository size limits](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)

## Summary

✅ **Prevention measures are in place**  
⚠️ **History cleanup is optional but recommended**  
📊 **Monitor repository size regularly**  
🎯 **Choose the right strategy for your team's needs**

For questions or assistance, refer to the documentation above or contact the repository maintainers.
