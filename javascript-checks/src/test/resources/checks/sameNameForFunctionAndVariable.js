var fun1;
function fun1() {              // NOK
}

function fun2() {
}
var fun2;                      // NOK

var fun3;
var fun = function fun3() {
}

var foo1;
function* foo1() {             // NOK
}

function* foo2() {
}
var foo2;                      // NOK

var foo3;
var foo = function* foo3() {
}

let fun4;
function fun4() {              // NOK
}

const fun5 = "";               // NOK
function fun5() {
}
