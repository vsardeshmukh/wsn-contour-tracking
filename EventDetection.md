Types of events in contour tracking applications and how to detect them.

# Introduction #

There are seven types of events worthy of notice. They can be detected by comparing two continuous system snapshots of blobs.

# Details #

  1. FORM: a new blob comes into existence, causing the number of blobs to increase.
  1. VANISH: a blob disappears, causing the number of blobs to decrease.
  1. MOVE: a blob neighboring or intersecting with the blob of the same size in the previous snapshot.
  1. MERGE: a blob intersecting with two or more blobs in the previous snapshot.
  1. SPLIT: a blob in the previous snapshot intersecting with two or more blobs in the latest snapshot.
  1. EXPAND: a blob intersecting with or neighboring only one blob in the previous snapshot expands.
  1. SHRINK: a blob intersecting with or neighboring only one blob in the previous snapshot shrinks.
  1. GLOBAL EXPAND: the total size of blobs increases.
  1. GLOBAL SHRINK: the total size of blobs decreases.