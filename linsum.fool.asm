push 0
lfp
push function0
lfp
push function3
lfp
push function4
lfp
push 2
lfp
stm
ltm
push -4
add
lw
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
push 5
lfp
stm
ltm
push 2
add
lw
ltm
push 1
add
lw
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
push 1
beq label0
lfp
lfp
lw
stm
ltm
push -6
add
lw
ltm
push -7
add
lw
js
b label1
label0:
lfp
push 3
add
lw
lfp
push -2
add
lw
add
label1:
stm
pop
pop
pop
sra
pop
pop
pop
pop
sfp
ltm
lra
js

function1:
cfp
lra
push 20
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
push 1
add
lw
mult
bleq label2
push 0
b label3
label2:
push 1
label3:
stm
sra
pop
pop
pop
sfp
ltm
lra
js

function2:
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
push 1
add
lw
mult
stm
sra
pop
pop
pop
pop
sfp
ltm
lra
js

function3:
cfp
lra
lfp
push function1
lfp
push function2
lfp
push 10
lfp
stm
ltm
push -2
add
lw
ltm
push -3
add
lw
lfp
lw
stm
ltm
push -2
add
lw
ltm
push -3
add
lw
js
stm
pop
pop
pop
pop
sra
pop
pop
sfp
ltm
lra
js

function4:
cfp
lra
push 1
stm
sra
pop
sfp
ltm
lra
js