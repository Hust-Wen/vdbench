Vdbench distributions usually come at a minimum with shared libraries compiled for:
- Windows
- Solaris, both Sparc and X86
- Linux, x86

I don't have access to other platforms, for those you'll have to do your
own compile and link.

In this /Jni directory you will find numerous make.xxx scripts that are used or
have been used for platform compiles.

You will need a java 1.7 or higher '#include' directory for the C compile.
Make modifications needed and make sure, and that some times is the hard part,
that all compile and link parameters are defined properly.

Outside of Windows and Solaris you will likely see loads of compiler warning messages.
Those are almost always related to small typecast differences between the platforms.
I have only tried to keep both Windows and Solaris clean.
All other platform: just ignore those warnings, I never have seen any real problems.

Some times I get error messages of some #defines or typecast statements being done
more than once, typically that shows up in vdbjni.h
Just make any change necessary.
As far as I understand this is mainly caused by a target OS having made some
small changes to their own include files.

These are the platforms that have ever successfully been using Vdbench:

- Windows
- Solaris x86 and Sparc
- Linux x86 and Sparc
- OSX
- HP/UX
- AIX
- RaspberryPi
- ZLinux
- Power PC linux

Java source file Vdb/common.java, method common.get_shared_lib() translates the
information it gets from java to decide what platform it is running on and then
will load the proper shared library file.
If you have a new/weird platform to add, make your change there if needed.
Just run './vdbench -t', and file output/logfile.html will show you the data
Vdbench uses for this translation.


Once the compile is done, simply running './vdbench -t' should be all you need
to verify that everything works.


Have fun!



Henk.
