应用在Hadoop上的Pairwise Affinities
=============================

This is a proof-of-concept for generating a pairwise affinity matrix on a map-reduce environment (such as Hadoop). 
This affinity matrix could then be used for something like [spectral clustering on Mahout](http://mahout.apache.org/users/clustering/spectral-clustering.html).

The initial job of counting the raw data and numbering it accordingly was taken almost in its entirety from this 
[June 2012 post on waredingen.nl](http://waredingen.nl/monotonically-increasing-row-ids-with-mapredu).

The initial job can likely also be factored out in a framework like Mahout.
 Replacing it would need to be something like the text-to-SequenceFile conversion job. Effectively the purpose of the first job is to

 1. Determine how many data points there are, and
 2. Index each data point, making it aware of where it is in the entire set.

By whatever other means these objectives can be accomplished, then the initial job can be scrapped entirely.

使用指南
----------

 1. Provide a text file (or directory of text files) where each line is an n-dimensional point, each separated by a comma. For example:

        -1.67454329673,-1.65982727489
        -1.62661761615,-1.68596505587
        -1.42286584711,-1.66193468748
        -1.58282334852,-1.82754404428
        -1.50182036382,-1.62347000619
        -1.52781414121,-1.67665119321
        ...

 2. Run `RowNumberJob` on this data.

 3. Look in `PairwiseJob$BlockReducer.evaluate()` and modify this to whatever you want your pairwise operation to be (currently an RBF kernel).

 4. Run `PairwiseJob` on the output of #2, providing a blocking factor (`h`) and the number of data points (`v`).

 5. That's it, you're done!


依赖环境
------------

 - Hadoop 2.2.0
 - Java 7
