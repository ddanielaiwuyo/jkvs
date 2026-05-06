 #!/bin/env bash
 set -e
 LIMIT=1000
 for ((i=LIMIT; i>0; i--))
 do
	 # if (( $i % 3 ==  0 )); then
	 #  ./app rm key_$i 
	 #  ./app set key_$i value_$i 
	 #  break
	 # fi
	 ./app set key_$i value_$i
 done
