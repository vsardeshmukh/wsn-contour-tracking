Essential git instructions.

# Introduction #

  * clone the [repository](https://code.google.com/p/wsn-contour-tracking/source/checkout)
Add the following to your ~/.netrc.
```
machine code.google.com login farleylai@dynagrid.net password [generated googlecode.com password]
```
You need to follow the link to get the generated password.

Make sure the clone URL doesn't contain your username:
```
git clone https://code.google.com/p/wsn-contour-tracking/trunk
```

  * familiar with commands to add or move files
```
$> git add file1 file2
$> git mv path1 path2
```

  * change default editor for log/commit message
```
$> git config --global core.editor "vim"
```

  * to commit changes to local repository
```
$> git commit
```

  * to push changes so far to the google code repository
```
First time push:
$> git push --all
Otherwise,
$> git push
```

  * to pull changes from the google code repository
```
$> git pull
```

# Details #

Add your content here.  Format your content with:
  * Text in **bold** or _italic_
  * Headings, paragraphs, and lists
  * Automatic links to other wiki pages