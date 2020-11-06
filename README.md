# MicroCisc Kotlin

Emulator/VM implementation for https://github.com/grokthis/ucisc

## Installation

##### Step 1

Install JVM (tested with open JDK 14)

For Ubuntu:
```
$ sudo apt install openjdk-14-jdk
```

##### Step 2

Download the latest release from the
(releases page)[https://github.com/grokthis/ucisc-kotlin/releases].
Extract the tar and copy the jar to `~/.local/lib/ucisc/ucisc.jar`
and copy the `ucisc` script to `~/.local/bin/ucisc`. Make sure `~/.local/bin`
is in your path.

## Usage

The `ucisc` command combines the compilation and VM execution. To compile
a file:

```
$ ucisc -c <file.ucisc> > out.hex
```

It will dump the compiled hex on stdout after compiling. You can redirect to
a file of your choice. To run the emulator:

```
$ ucisc <file.ucisc>
```

It will compile and run the results in a standard ucisc machine.

## Contributing

Bug reports and pull requests are welcome on GitHub at https://github.com/grokthis/micro_cisc. This project is intended to be a safe, welcoming space for collaboration, and contributors are expected to adhere to the [code of conduct](https://github.com/grokthis/micro_cisc/blob/master/CODE_OF_CONDUCT.md).

## License

The gem is available as open source under the terms of the [MIT License](https://opensource.org/licenses/MIT).

## Code of Conduct

Everyone interacting in the MicroCisc project's codebases, issue trackers, chat rooms and mailing lists is expected to follow the [code of conduct](CODE_OF_CONDUCT.md).
