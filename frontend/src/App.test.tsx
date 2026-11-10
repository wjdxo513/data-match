import { render, screen } from '@testing-library/react';
import App from './App';

describe('App', () => {
  it('브랜드 이름을 렌더한다', () => {
    render(<App />);
    expect(screen.getByText('DataMatch')).toBeInTheDocument();
  });
});
