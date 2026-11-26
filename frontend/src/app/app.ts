import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { cuidarDaRolagem } from './compartilhado/rolagem';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `<router-outlet />`,
})
export class App {
  constructor() {
    cuidarDaRolagem();
  }
}
