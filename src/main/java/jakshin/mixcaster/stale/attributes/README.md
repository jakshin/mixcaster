The code in this package that reads/writes user-defined file attributes was tested 
on various OSes & file systems, running on OpenJDK 17.0, in December 2021.
 
- On macOS, it works on APFS and HFS+, and FAT-16 and ExFAT using dot-underscore files.
- When running on Windows Java, it works on NTFS but not the WSL-hosted ext4 file system;  
  conversely, when running on Unix Java in WSL, it works on ext4, but not NTFS.
- On GNU/Linux, it works on ext4, which most mainstream desktop distros use by default,  
  but it doesn't work on Fedora 35's btrfs or CentOS 8's xfs.
- It works on GhostBSD 21's zfs, but not MidnightBSD 2.1's ufs.

It was also tested on Haiku R1/beta3, on OpenJDK 14, the latest version currently available there.  
FileStore's supportsFileAttributeView("user") returns false,
but if you ignore that and set/get attributes anyway, it works!
