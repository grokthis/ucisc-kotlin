# Microcode Compiler written in MicroCode
#
# The goal of this compiler is to start the bootstrap process into a uCISC higher
# level language. At the moment we have compilers written in other languages. We would
# like to change that and use uCISC to compile uCISC code.
#
# How it works:
# 1. Once started, the compiler initializes a serial device
# 2. The compiler will watch for compilable code on the Rx serial
# 3. Any TEST line will be ignored, for now
# 4. Any comment or blank line will be ignored
# 5. Microcode will attempt to be compiled. Hex string will be streamed back to Tx.

################
# Syntax Setup #
################
def push as 1
def pop as 1
def none as 0

def pc as 0
def stk as 1
def r1 as 1
def r2 as 2
def r3 as 3
def val as 4
def bnk as 4
def &stk as 5
def &r1 as 5
def &r2 as 6
def &r3 as 7
def flags as 8
def b1 as 9
def b2 as 10
def b3 as 11
def int as 12
def &b1 as 13
def &b2 as 14
def &b3 as 15

# Conditionals
def zero? as 0
def !zero? as 1
def neg? as 2
def flags as 3
def store as 4
def oflow? as 5
def error? as 6
def int? as 7

# Compute Ops
def copy as 0
def and as 1
def or as 2
def xor as 3
def inv as 4
def shl as 5
def shr as 6
def swap as 7
def msb as 8
def lsb as 9
def add as 10
def sub as 11
def mult as 12
def div as 13
def oflw as 14
def wflw as 15

def devId as 0
def devType as 1
def initDev as 2
def TxSize as 7
def TxAvail as 7
def TxWrite as 8
def RxSize as 9
def RxAvail as 9
def RxRead as 10

def cEOT as 3
def cNewline as 10
def cHash as 35
def cZero as 48
def cNine as 57
def cTokenStrt as 33
def cTokenEnd as 127

######################
# Init Serial Device #
######################

# Initialize the stack to start at 4k
#  op   src  imm        dst  off        inc  eff
   copy val  4096       &stk 0          -    store  # Init stack to 4k

# A: 2
# Loop through all devices and find the first serial device
#  op   src  imm        dst  off        inc  eff
   copy val  2          &b1  -          -    store
   mult val  16         &b1  -          -    store  # Multiply device by 16 to get control space
:loopCtrl
   lsb  b1   devType    &r2  -          -    store  # Copy deviceType LSB to r2
   sub  val  4          &r2  -          -    flags  # subtract 4 from r2 (serial device id)
   copy pc   initSerial pc   -          -    zero?  # break if 0
   add  val  16         &b1  -          -    store  # Add 16 to control bank pointer
   sub  val  4096       &b1  -          -    flags  # subtract 0x1000, end of control space
   copy pc   loopCtrl   pc   -          -    !zero? # loop if not zero
   copy pc   0          pc   -          -    store  # Error, no serial device, halt
:initSerial
   copy val  0          &b2   -         -    store  # Load self control into &b2
   copy b2   devId      b1   initDev    none store  # Copy our id into initDevice on serial device
   copy &b1  0          stk  0          push store  # Push device control to stack

#############
# Main Code #
#############
# We will use a separate instruction stack to push parsed instructions to. For now, this
# stack will simply be a word indicating record length and 2 words for the instruction.
# The record length isn't strictly necessary at the moment but supporting labels in the
# future will require them.
#
# Basic logic is to parse one token at a time until a full arg is parsed, then take action
# on the arg, updating the state of the current instruction. At the end, push to the
# instruction stack.
#
# Memory space:
# 0-4k: Code, SP starts at 4k
# 4-8k: defines
# 8-64k: compiled code list
#
# Instruction list is a linked list of instruction meta data:
# - pointer to next list item
# - instruction address
# - MSW instruction word
# - LSW instruction word
# - Immediate label length, immediate label string (1 word per char)
# - Label length, label string (1 word per char)
#
# - Defines are: pointer to next, name length, name words, val length, val words
#
# Compiler can handle 40k words of code or 20k instructions before running out of space.
# This should be sufficient for most applications that run on the system as the other 24k
# is likely to be needed for data. Beyond this limit, you'll have to split the compilation
# into multiple files/steps.
#
# A: 26
#  op   src  imm        dst  off        inc  eff
   copy val  2          stk  0          push store  # Push defines list MS nibble
   shl  val  11         stk  0          none store  # Convert defines list nibble into pointer
   copy val  0          stk  0          push store  # Push instruction list pointer

   copy val  0          stk  0          push store  # Push negative flag to stack (0)
   copy val  0          stk  0          push store  # Push arg count to stack (0)

#####################
# Step 1 - Next Arg #
#####################

# Get next char, if EOT we are done, transmit over Tx
# A: 36
#  op   src  imm        dst  off        inc  eff
   copy pc   6          stk  0          push store  # Copy return address to stack
   copy stk  5          stk  0          push store  # Copy deviceControl to top of stack as arg
   copy pc   nextToken  pc   -          -    store  # Call nextToken
   sub  val  cEOT       stk  0          none flags  # 42: Subtract ASCII EOT from return char
   copy pc   0          pc   -          -    zero?  # Halt if zero for debug, will be jump to end

   copy pc   0          pc   -          -    store  # Halt for debug

##########
# readRx #
##########

# returns 8 bit character from Rx on stack in LSB
# Wait for bytes to be readable
# fun readRx: deviceControl = stack
:readRx
#  op   src  imm        dst  off        inc  eff
   copy stk  0          &b1  -          push store  # Pop deviceControl from stack into &b1
:loopRx
   lsb  b1   RxAvail    stk  0          none flags  # Check for Rx buffer available
   copy pc   loopRx     pc   -          -    zero?  # loop if zero bytes available

# We have at least one byte available in Rx
# Read and return
#  op   src  imm        dst  off        inc  eff
   copy stk  0          &r2  -          pop  store  # Pop return address into &r2
   copy b1   RxRead     stk  0          push store  # Push Rx read value onto stack, will be incoming byte
   copy &r2  0          pc   -          -    store  # Jump return

##############
# end readRx #
##############

#############
# nextToken #
#############

# Takes a device control to read from
# Returns:
# - Character is the next valid token character, 0x3 EOT or number
# var character = fun nextToken: deviceControl = stack
:nextToken
#  op   src  imm        dst  off        inc  eff
   copy pc   6          stk  0          push store  # Copy return address to stack
   copy stk  1          stk  0          push store  # Duplicate device control on stack
   copy pc   readRx     pc   -          -    store  # Call readRx: deviceControl

   sub  val  cHash      stk  0          none flags  # Subtract 35 (ascii for `#`) from returned value
   copy pc   comment    pc   -          -    zero?  # Break to comment if zero

   sub  val  cNewline   stk  0          none flags  # Subtract 10 (ascii for `\n`) from returned value
   copy 5    1          5    -          -    zero?  # Pop char off stack if zero
   copy 0    nextToken  0    -          -    zero?  # Jump to next loop if zero

   sub  val  cEOT       stk  0          none flags  # Subtract 3 (end of text) from return char
   copy pc   found      pc   -          -    none   # Break to found if zero (end of text)

   sub  val  cTokenStrt stk  0          none flags  # Subtract ascii `!` from returned value
   copy pc   comment    pc   -          -    neg?   # Break to comment if negative (it's not a token)

   sub  val  cTokenEnd  stk  0          none flags  # Subtract ascii for `~` + 1 from returned value
   copy pc   found      pc   -          -    neg?   # Break to found if negative (it is a token)

#  Read the rest of the line, this is a comment
:comment
#  op   src  imm        dst  off        inc  eff
   copy pc   6          stk  0          0    store  # Copy return address to stack, overwrite char
   copy stk  1          stk  0          1    store  # Copy device control on stack
   copy pc   readRx     pc   -          -    store  # Call readRx: deviceControl
   sub  val  cEOT       stk  0          0    flags  # Subtract 3 (end of text) from return char
   copy pc   found      pc   -          -    zero?  # Break to found if zero (end of text)
   sub  val  cNewline   stk  0          0    flags  # Subtract 10 (newline) from return char
   copy pc   comment    pc   -          -    !zero? # Loop to read next char if not zero
   copy &r1  1          &r1  -          -    store  # Pop char off stack
   copy pc   nextToken  pc   -          -    zero?  # Loop to start if zero to read next line

:found
# We found a character, return it
#  op   src  imm        dst  off        inc  eff
   0    1    2          6    -          0    4    # Copy return address to &r2
   0    1    0          1    2          0    4    # Copy character to return value
   0    5    2          5    -          -    4    # Pop stack to return character
   0    6    0          0    -          -    4    # Jump return

#################
# end nextToken #
#################

##########
# STEP 2 #
##########

# parse integers until Rx returns non-numeric value, accumulating the value on the stack
#  op   src  imm        dst  off        inc  eff
   11   4    48         1    0          0    4    # accumulator = Subtract char 0 (48) to turn char into number
   0    0    4          0    -          -    0    # jump to hyphen if negative
   0    0    6          0    -          -    4    # jump to read next
# fun hyphen:
# Hyphen ends up as zero unless it prefixes a number, we keep track by setting a negative flag that gets multiplied
# by the first number to come up. Otherwise, accumulator ends up at the next step as 0.
   0    4    -1         1    3          0    4    # Store -1 in negative flag
   0    4    0          1    0          0    4    # Set accumulator to 0

:readNext
   0    1    2          1    0          1    4    # Duplicate device control on stack
   0    0    readRx     0    -          -    4    # Call readRx: deviceControl
   11   4    3          1    0          0    3    # Subtract 3 (end of text) from return char
   0    0    X          0    -          -    0    # Break if zero to finish and transmit (TODO: detect EO instruction)
   11   4    84         1    0          0    3    # Subtract 57 (ascii for `9`) from returned value
   0    0    22         0    -          -    1    # Break to step 3 if positive (not a number)
   11   4    48         1    0          0    4    # Subtract char 0 (48) to turn char into number
   0    0    18         0    -          -    2    # break to step 3 if negative (not a number)

   12   4    10         1    1          0    4    # Multiply accumulator by 10
   10   1    0          1    1          0    4    # Add new number to accumulator
   0    5    1          5    -          -    4    # pop new number from stack
   10   4    0          1    3          0    4    # add 0 to negative flag
   0    0    -24        0    -          -    0    # Loop to read next if zero
:hyphen
   12   4    -1         1    0          0    4    # Multiply accumulator by -1
   0    4    0          1    3          0    4    # Save 0 to negative flag since we handled it
   0    0    -30        0    -          -    4    # Loop to read next

# Step 3: after the end of the number, bit manipulate it into place according to arg count
#  op   src  imm        dst  off        inc  eff
   10   4    0          1    1          0    3    # Add 0 to arg count to set flags
   0    0    12         0    -          -    1    # Next condition if not zero
# Op
   1    4    15         1    0          0    4    # arg is op, and with 0xF
   2    1    0          1    X          0    4    # or arg with upper instruction word
   0    0    X          0    -          -    4    # jump to step 4
   11   4    1          1    1          0    3    # Sub 1 from arg count to set flags
   0    0    X          0    -          -    1    # Next condition if not zero
# Source
   1    4    15         1    0          0    4    # arg is source, and with 0xF
   5    4    12         1    0          0    4    # arg is source, shl 12
   2    1    0          1    X          0    4    # or arg with upper instruction word
   0    0    X          0    -          -    4    # jump to step 4
   11   4    1          1    1          0    3    # Sub 2 from arg count to set flags
   0    0    X          0    -          -    1    # Next condition if not zero
# Immediate
   0    1    0          1    X          0    4    # set lower instruction word to immediate
   0    0    X          0    -          -    4    # jump to step 4
   11   4    2          1    1          0    3    # Sub 3 from arg count to set flags
   0    0    X          0    -          -    1    # Next condition if not zero
# Destination
# Special case, if destination is mem, make room for offset
   11   4    1          1    0          0    3    # Subtract 1 from destination
   0    0    X          0    -          -    2    # Jump destination continued if negative
   11   4    4          1    0          0    3    # Subtract 4 from destination
   0    0    X          0    -          -    2    # Jump clear offset if negative
   11   4    9          1    0          0    3    # Subtract 9 from destination
   0    0    X          0    -          -    2    # Jump destination continued if negative
   11   4    12         1    0          0    3    # Subtract 12 from destination
   0    0    X          0    -          -    2    # Jump clear offset if negative
   0    0    X          0    -          -    4    # Jump destination continued
# Clear offset
   0    4    4095       6    -          -    4    # Copy 0x0FFF into &r2
   1    6    0          1    X          -    4    # And &r2 with lower instruction
# Destination continued
   1    4    15         1    0          0    4    # arg is destination, and with 0xF
   5    4    8          1    0          0    4    # arg is source, shl 8
   2    1    0          1    X          0    4    # or arg with upper instruction word
   0    0    X          0    -          -    4    # jump to step 4
   11   4    3          1    1          0    3    # Sub 4 from arg count to set flags
   0    0    X          0    -          -    1    # Next condition if not zero
# Offset
   1    4    15         1    0          0    4    # arg is offset, and with 0xF
   5    4    12         1    0          0    4    # arg is offset, shl 12
   2    1    0          1    X          0    4    # or arg with lower instruction word
   0    0    X          0    -          -    4    # jump to step 4
   11   4    3          1    1          0    3    # Sub 5 from arg count to set flags
   0    0    X          0    -          -    1    # Jump to effect if not zero
# Inc
   1    4    1          1    0          0    4    # arg is increment, and with 0x1
   5    4    7          1    0          0    4    # arg is offset, shl 7
   2    1    0          1    X          0    4    # or arg with upper instruction word
   0    0    X          0    -          -    4    # jump to step 4
# Effect
   1    4    7          1    0          0    4    # arg is effect, and with 0x7
   5    4    4          1    0          0    4    # arg is effect, shl 4
   2    1    0          1    X          0    4    # or arg with upper instruction word

# Step 4: increment arg and continue
   10   4    1          1    1          -    4    # Add 1 to arg count
   11   4    7          1    1          0    3    # Sub 7 from arg count
   0    0    X          0    -          -    2    # Jump to read next arg if negative


# Step 5: if that was the last arg, push to instruction stack and reset accumulation vars
   0    1    X          3    0          1    4    # Push lower instruction word to instruction stack
   0    1    X          3    0          1    4    # Push upper instruction word to instruction stack
   0    4    2          3    0          1    4    # Push length of 2 to instruction stack
   0    4    0          1    X          0    4    # Copy 0 to lower instruction word
   0    4    0          1    X          0    4    # Copy 0 to upper instruction word
   0    4    0          1    X          0    4    # Copy 0 to arg count
   0    5    X          5    -          -    4    # Pop vars off stack
   0    0    X          0    -          -    4    # Jump to read next arg

#######################
# Finish and Transmit #
#######################

# Transmit the stack to Rx from bottom to top (FIFO)

