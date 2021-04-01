push 0
push 2
lfp
push function0
lfp
push 7
push 5
lfp
stm
ltm
push -3
add
lw
ltm
push -4
add
lw
js
print
halt

function0:
cfp
lra
lfp
push 1
add
lw
lfp
push 2
add
lw
add
lfp
lw
push -2
add
lw
mult
stm
sra
pop
pop
pop
sfp
ltm
lra
js