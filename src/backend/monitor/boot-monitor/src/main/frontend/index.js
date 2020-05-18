import monitor from './monitor';

// tag::customization-ui-toplevel[]
global.SBA.use({
  install({ viewRegistry }) {
    viewRegistry.addView({
      name: 'monitor',  //<1>
      path: '/monitor', //<2>
      component: monitor, //<3>
      label: 'Monitor', //<4>
      order: 3, //<5>
    });
  }
});
// end::customization-ui-toplevel[]


