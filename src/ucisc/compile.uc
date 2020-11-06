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

# Define a bunch of useful values
# Stack end address
def stackEnd as 4096

# Device control block offsets
def devId as 0
def devType as 1
def initDev as 2
def TxSize as 7
def TxAvail as 7
def TxWrite as 8
def RxSize as 9
def RxAvail as 9
def RxRead as 10

def ctrlMemEnd as 4096
def devTypeSerial as 4
def ctrlBlockSize as 16

######################
# Init Serial Device #
######################

# Initialize the stack to start at 4k
#  op   src  imm        dst
   copy val  stackEnd   &stk # Init stack

# Loop through all devices and find the first serial device
    copy val  2                   &rb1 # Start at device 2
    mult val  ctrlBlockSize       &rb1 # Multiply to get control space

    # Loop through devices and find a serial device
    {
        lsb  b1   devType         &r2
        sub  val  devTypeSerial   &r2  flags
        copy pc   break           pc   zero?

        add  val  ctrlBlockSize   &rb1
        sub  val  ctrlMemEnd      &rb1 flags  # Check for end of control mem space
        copy pc   loop            pc   !zero?
        copy pc   0               pc          # Error, no serial device found, halt
    }

   copy val  0      &b2            # Load self control into &b2
   copy b2   devId  b1 ctrlInitDev # Copy our id into initDevice on serial device
   copy &b1  0      stack  0  push # Push device control to stack

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
# - Defines are: pointer to next, val length, val words, name length, name words
#
# Compiler can handle 40k words of code or 20k instructions before running out of space.
# This should be sufficient for most applications that run on the system as the other 24k
# is likely to be needed for data. Beyond this limit, you'll have to split the compilation
# into multiple files/steps.
#
# A: 26
#  op   src  imm        dst  off        inc  eff
   copy val  8192       &r2  -          -    store    # Load defines pointer into r2
   copy val  0          r2   0          push store    # Push last, empty define
   copy val  0          r2   0          push store    # Push last, empty define
   copy val  0          r2   0          push store    # Push last, empty define

   copy &r2  0          stk  0          push store    # Push defines pointer to stack
   copy val  0          stk  0          push store    # Push instruction list pointer
   copy val  0          stk  0          push store    # Push arg count to stack
   copy val  0          stk  0          push store    # Push arg length to stack

#########################
# Step 1 - Get Next Arg #
#########################
#  op   src  imm        dst  off        inc  eff
   copy pc   6          stk  0          push store    # Copy return address to stack
   copy stk  5          stk  0          push store    # Copy deviceControl to top of stack as arg
   copy pc   nextArg    pc   -          -    store    # Call nextArg

#################
# Check for def #
#################
#  op   src  imm        dst  off        inc  eff
   sub  val  3          stk  0          none flags    # Check if length is correct
   copy pc   notDef     pc   -          -    !zero?   # If length is wrong, notDef
   sub  val  102        stk  1          none flags    # Subtract f (string in reverse order)
   copy pc   notDef     pc   -          -    !zero?   # If f is wrong, notDef
   sub  val  101        stk  2          none flags    # Subtract f (string in reverse order)
   copy pc   notDef     pc   -          -    !zero?   # If e is wrong, notDef
   sub  val  100        stk  3          none flags    # Subtract f (string in reverse order)
   copy pc   notDef     pc   -          -    !zero?   # If d is wrong, notDef
# It is a def!
# next arg should be name
   add  stk  0          &stk -          -    store    # Pop string chars from stack
   copy &stk 1          &stk -          -    store    # Pop string length from stack

   copy pc   6          stk  0          push store    # Copy return address to stack
   copy stk  4          stk  0          push store    # Copy deviceControl to top of stack as arg
   copy pc   nextArg    pc   -          -    store    # Call nextArg

   copy &stk 2          &r2  -          -    store    # Copy stack offset to defs pointer
   add  stk  0          &r2  -          -    store    # Add string length to defs pointer
   copy r2   0          &r2  -          -    store    # Load defs pointer to r2

#  Create new def
   copy &stk 0          &r3  -          -    store    # Copy stack reference to r3
   add  stk  0          &r3  -          -    store    # Point r3 to end of string

   copy val  0          r2   0          push store    # Push name length of 0
# while remaining to copy to def
:whCpyToDef
   sub  r2   0          stk  0          none flags    # Break if zero
   copy pc   bkCpyToDef pc   -          none zero?    # Break out of copy def if length has been copied
   add  val  1          r2   0          push store    # add 1 to length, push
   copy r3   0          r2   1          none store    # Copy char to def
   copy &r3  -1         &r3  -          -    store    # Decrement r3 pointer
   copy pc   whCpyToDef pc   -          -    store    # Loop to next char
:bkCpyToDef
   add  stk  0          &stk -          -    store    # Pop string chars from stack
   copy &stk 1          &stk -          -    store    # Pop string length from stack
   copy val  0          r2   0          push store    # Push label length of 0
   copy &r2  2          r2   0          push store    # Push address of next define
   add  r2   2          r2   0          none store    # Add string length to next define addr

# second arg should be "as"
   copy pc   6          stk  0          push store    # Copy return address to stack
   copy stk  3          stk  0          push store    # Copy deviceControl to top of stack as arg
   copy pc   nextArg    pc   -          -    store    # Call nextArg

   sub  val  2          stk  0          none flags    # Check if length is correct
   copy pc   notAs      pc   -          -    !zero?   # If length is wrong, notDef
   sub  val  115        stk  1          none flags    # Subtract f (string in reverse order)
   copy pc   notAs      pc   -          -    !zero?   # If f is wrong, notDef
   sub  val  97         stk  2          none flags    # Subtract f (string in reverse order)
   copy pc   notAs      pc   -          -    !zero?   # If e is wrong, notDef
# Handle as (just get next arg)
   copy pc   0          pc   -          -    store    # Halt for debug
   add  stk  0          &stk -          -    store    # Pop string chars from stack
   copy &stk 1          &stk -          -    store    # Pop string length from stack
   copy pc   6          stk  0          push store    # Copy return address to stack
   copy stk  2          stk  0          push store    # Copy deviceControl to top of stack as arg
   copy pc   nextArg    pc   -          -    store    # Call nextArg

:notAs
   copy pc   0          pc   -          -    store    # Halt for debug

# third arg should be value

:notDef

################
# Fun :nextArg #
################

# Get next char, if EOT we are done, transmit over Tx
# A: 36
# var string = :nextArg deviceControl = stack
:nextArg
#  op   src  imm        dst  off        inc  eff
   copy pc   6          stk  0          push store  # Copy return address to stack
   copy stk  1          stk  0          push store  # Copy deviceControl to top of stack as arg
   copy pc   nextToken  pc   -          -    store  # Call nextToken
   sub  val  cEOT       stk  0          none flags  # 42: Subtract ASCII EOT from return char
   copy pc   0          pc   -          -    zero?  # Halt if zero for debug, will be jump to end

# If arg is not started, throw away spaces
#  op   src  imm        dst  off        inc  eff
   add  val  0          stk  2          none flags  # Add 0 to arg length to set flags
   copy pc   capture    pc   -          -    !zero? # Token already started, capture it
   sub  val  cSpace     stk  0          none flags  # Subtract space char from returned char
   copy &r1  1          &r1  -          -    zero?  # pop char if space
   copy pc   nextArg    pc   -          -    zero?  # loop back to beginning if space and arg is zero len

# Else, capture all token chars until white space
:capture
#  op   src  imm        dst  off        inc  eff
   sub  val  cSpace     stk  0          none flags  # Subtract space from character
   copy &r1  1          &r1  0          -    zero?  # pop extra char off stack
   copy pc   endArg     pc   -          -    zero?  # Jump to end of arg if space
# Capture the character in the arg
   copy stk  0          stk  0          push store  # duplicate char
   copy stk  2          stk  1          none store  # duplicate Rx device on stack
   copy stk  3          stk  2          none store  # duplicate return address
   copy stk  4          stk  3          none store  # duplicate arg length
   add  val  1          stk  3          none store  # add 1 to arg length
   copy stk  0          stk  4          none store  # copy char into string, note string order is reversed
   copy &r1  1          &r1  0          -    store  # pop extra char off stack
   copy pc   nextArg    pc   -          -    store  # loop jump to get next char

:endArg
# We hit a space and have non-zero arg length
#  op   src  imm        dst  off        inc  eff
   copy &stk 1          &stk 0          -    store  # pop device off stack, return string on stack
   copy stk  0          pc   0          pop  store  # pop return address into pc

#  first, if def, create a new definition

#  first, substitute any existing defs
#  if first arg, test for TEST line and parse
#  if first arg, test for def and parse
#  else parse number
#  when (argCount)
#    0 -> op code, upper.or(number.and(0xF))
#    1 -> src code, upper.or(number.and(0xF).shl(12))
#    2 -> imm val, store in data structure
#    3 -> dest code, upper.or(number.and(0xF).shl(8))
#    4 -> source val, lower.or(number.and(0xF).shl(12))
#    5 -> increment val, upper.or(number.and(0x1).shl(7))
#    6 -> effect val, upper.or(number.and(0x7).shl(4))
#  else increment arg found

###########
# strComp #
###########
# var result = :strComp source, target = stack
# Returns a 0 (false) or 1 (true) if the source and target strings match
# Sets the flags register appropriately for zero/not zero before return
#  op   src  imm        dst  off        inc  eff


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

   sub  val  cSpace     stk  0          none flags  # Subtract space from return char
   copy pc   found      pc   -          -    none   # Break to found if zero

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

