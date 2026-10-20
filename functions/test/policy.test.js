const {test}=require('node:test');const assert=require('node:assert/strict');
const {transition,validateMinutes}=require('../policy');
const s={learner:'l',mentor:'m',members:['l','m'],status:'requested',confirmations:[]};
test('duration accepts quarter-hours and rejects invalid amounts',()=>{assert.equal(validateMinutes(60),60);for(const x of [0,-1,NaN,Infinity,14,16,181,15.5,'60'])assert.throws(()=>validateMinutes(x));});
test('outsiders and learner cannot accept',()=>{assert.throws(()=>transition(s,'x','accept'));assert.throws(()=>transition(s,'l','accept'));});
test('two distinct confirmations settle exactly once',()=>{const accepted=transition(s,'m','accept');const one=transition(accepted,'l','complete');assert.equal(one.status,'accepted');const duplicate=transition(one,'l','complete');assert.equal(duplicate.confirmations.length,1);const done=transition(one,'m','complete');assert.equal(done.status,'completed');assert.deepEqual(transition(done,'m','complete'),done);});
test('cannot cancel completed or partially confirmed sessions',()=>{const a=transition(s,'m','accept');const one=transition(a,'l','complete');assert.throws(()=>transition(one,'m','cancel'));assert.throws(()=>transition(transition(one,'m','complete'),'l','cancel'));});
test('pending requests can be cancelled and cannot be completed',()=>{assert.equal(transition(s,'l','cancel').status,'cancelled');assert.throws(()=>transition(s,'l','complete'));});
