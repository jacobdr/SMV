# Spark Modularized View (SMV)
Spark Modularized View enables users to build enterprise scale applications on Apache Spark platform.

* [Getting Started](#smv-getting-started)
* [Installation Guide](docs/user/smv_install.md)
* [User Guide](docs/user/0_user_toc.md)
* [API docs](http://tresamigossd.github.io/SMV/scaladocs/index.html#org.tresamigos.smv.package)


# SMV Getting Started

## Installation

We avidly recommend using [Docker](https://docs.docker.com/engine/installation/) to install SMV. Using Docker, start an SMV container with

```
docker run -it --rm tresamigos/smv
```

If Docker is not an option on your system, see the [installation guide](docs/user/smv_install.md).

## Create Example App

SMV provides a shell script to easily create template applications. We will use a simple example app to explore SMV.

```shell
$ smv-init -s MyApp
```

## Run Example App

Run the entire application with

```shell
$ smv-pyrun --run-app
```

The output csv file and schema can be found in the `data/output` directory. Note that 'XXXXXXXX' here substitutes for a number which is like the version of the module.

```shell
$ cat data/output/com.mycompany.myapp.stage1.EmploymentByState_XXXXXXXX.csv/part-* | head -5
"50",245058
"51",2933665
"53",2310426
"54",531834
"55",2325877

$ cat data/output/com.mycompany.myapp.stage1.EmploymentByState_XXXXXXXX.schema/part-*
@delimiter = ,
@has-header = false
@quote-char = "
ST: String[,_SmvStrNull_]
EMP: Long
```

## Edit Example App

The `EmploymentByState` module is defined in `src/python/com/mycompany/myapp/stage1/employment.py`:

```shell
class EmploymentByState(SmvPyModule, SmvPyOutput):
    """Python ETL Example: employment by state"""

    def requiresDS(self):
        return [inputdata.Employment]

    def run(self, i):
        df = i[inputdata.Employment]
        df1 = df.groupBy(col("ST")).agg(sum(col("EMP")).alias("EMP"))
        return df1
```

The `run` method of a module defines the operations needed to get the output based on the input. We would like to filter the table based on if each row's state is greater or less than 1,000,000. To accomplish this, we need to add a filter to the `run` method:

```shell
  def run(self, i):
      df = i[inputdata.Employment]
      df1 = df.groupBy(col("ST")).agg(sum(col("EMP")).alias("EMP"))
      df2 = df1.filter((col("EMP") > lit(1000000))
      return df2
```

Now remove the old output and run the module again with

```shell
smv-pyrun --purge-old-output --run-app
```
(make sure you run this from the from the root of the project)

Inspect the new output to see the changes.

```shell
$ cat data/output/com.mycompany.myapp.stage1.EmploymentByState_XXXXXXXX.csv/part-* | head -5
"51",2933665
"53",2310426
"55",2325877
"01",1501148
"04",2027240

$ cat data/output/com.mycompany.myapp.stage1.EmploymentByState_XXXXXXXX.schema/part-*
@delimiter = ,
@has-header = false
@quote-char = "
ST: String[,_SmvStrNull_]
EMP: Long
```

Be sure to view the most recent


## smv-pyshell

We can also view the results in the smv-pyshell. To start the shell, run

```
$ smv-pyshell
```

To get the `DataFrame` of `EmploymentByState`,

```shell
>>> x = df('com.mycompany.myapp.stage1.EmploymentByState')

```

To peek at the first row of results,

```shell
>>> x.peek(1)
ST:String            = 50
EMP:Long             = 245058
cat_high_emp:Boolean = false
```

See the [user guide](docs/0_user_toc.md) for further examples and documentation.
