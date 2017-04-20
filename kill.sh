ps aux | grep allinone | grep -v grep | awk '{print $2}' | xargs kill
