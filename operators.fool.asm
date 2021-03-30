push 0
push 3
push 2
add
push 7
push 2
sub
push 0
push function0
lfp
push 0
lfp
push -2
add
lw
lfp
push -3
add
lw
add
lfp
stm
ltm
ltm
push -5
add
lw
js
print
halt

function0:
cfp
lra
push 2
push 1
lfp
lw
push -4
add
lw
beq label3
push 1
lfp
push 2
add
lw
beq label3
push 0
b label2
label3:
push 1
label2:
push 1
beq label0
lfp
push 1
add
lw
lfp
push -2
add
lw
div
b label1
label0:
lfp
push 1
add
lw
lfp
push -2
add
lw
mult
label1:
stm
pop
sra
pop
pop
pop
sfp
ltm
lra
js