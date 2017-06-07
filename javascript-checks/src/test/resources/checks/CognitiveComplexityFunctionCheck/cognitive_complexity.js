a; // Noncompliant {{Refactor this function to reduce its Cognitive Complexity from 25 to the 0 allowed.}}
if (condition) {    // +1
}

function zero_complexity(){
}

function if_else_complexity() {
    if (condition) {        // +1
    } else if (condition) { // +1
    } else {                // +1
    }
}

function else_nesting() {
    if (condition) {      // +1
    } else {              // +1 (nesting level +1)
        if (condition) {} // +2
    }
}

function loops_complexity() {
    while (condition) {             // +1 (nesting level +1)
        if (condition) {            // +2
        }
    }
}

function nesting_func_with_not_structural_complexity() {
    return a && b;  // +1
    function nested_func() {
        if (condition) { }     // +1
    }
}

function with_complexity_after_nested_function() {
    function nested_func() {   // (nesting level +1)
        if (condition) { }     // +2
    }

   if (condition) {}           // +1
}

var arrowFunction = (a, b) => a && b; // +1

var functionExpression = function(a, b) { return a && b; } // +1

function several_nested() {
  if (condition) {    // +1 (+1 for nesting)
    if (condition) {} // +2
    if (condition) {} // +2
  }
}

// Some spaghetti code
(function(a) {
  if (cond) {}  // +1
  return a;
})(function(b) {return b < 3 ? b + 1 : b /* +1 */})(0);
