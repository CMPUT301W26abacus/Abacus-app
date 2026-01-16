# Contributing to ABACUS App

Hey team! Here's how we keep things organized. 🙌

## 🌿 Branch Workflow

We use **feature branches** — never push directly to `main`!

### Branch Naming

Use this format: `type/short-description`

Examples:
- `feature/login-screen`
- `bugfix/crash-on-startup`
- `docs/update-readme`

### Creating a Branch

```bash
# Make sure you're on main and up to date
git checkout main
git pull origin main

# Create your feature branch
git checkout -b feature/your-feature-name
```

### Saving Your Work

```bash
# Stage your changes
git add .

# Commit with a meaningful message
git commit -m "feat: add login button to home screen"

# Push to GitHub
git push origin feature/your-feature-name
```

## 🔄 Pull Request Process

1. **Push your branch** to GitHub
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Open a Pull Request** on GitHub
   - Use the PR template (it'll auto-populate)
   - Fill in what you changed and why
   - Link any related issues

3. **Request a review** from at least 1 teammate

4. **Address feedback** — push new commits to the same branch

5. **Merge** once approved (squash merge preferred)

6. **Delete your branch** after merging (GitHub can do this automatically)

## 📝 Code Review Guidelines

When reviewing:
- Be kind and constructive 💬
- Test the changes locally if possible
- Check for obvious bugs or issues
- Approve if it looks good!

When receiving feedback:
- Don't take it personally — we're all learning!
- Ask questions if something is unclear

## 🐛 Reporting Issues

Use the issue templates:
- **Bug Report** — something's broken
- **Feature Request** — new idea or improvement

## 💡 Quick Tips

- Pull from `main` often to avoid merge conflicts
- Keep PRs small and focused when possible
- Write meaningful commit messages
- Ask for help if you're stuck!

---

Questions? Drop a message in our team chat! 💬
